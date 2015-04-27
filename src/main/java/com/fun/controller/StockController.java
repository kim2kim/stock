package com.fun.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/stock")
public class StockController {

	private Logger logger = LoggerFactory.getLogger(StockController.class);

	private final static String yahooHistoricalData = "http://real-chart.finance.yahoo.com/table.csv?s={TICKER}&a={STARTMONTH}&b={STARTDAY}&c={STARTYEAR}&d={ENDMONTH}&e={ENDDAY}&f={ENDYEAR}&g=d&ignore=.csv";

	@RequestMapping(value = "/parse", method = {RequestMethod.GET, RequestMethod.POST})
	public String parse(HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		logger.info("calling parse");
		
		String startDate = request.getParameter("startDate");
		String endDate = request.getParameter("endDate");
		String newURL = yahooHistoricalData;
		if(StringUtils.isEmpty(startDate) == true) {
			newURL = StringUtils.replace(newURL, "{STARTMONTH}", "01");
			newURL = StringUtils.replace(newURL, "{STARTDAY}", "01");
			newURL = StringUtils.replace(newURL, "{STARTYEAR}", "2014");
		}else {
			newURL = StringUtils.replace(newURL, "{STARTMONTH}", (Integer.parseInt(startDate.substring(4,6)) - 1) + "");
			newURL = StringUtils.replace(newURL, "{STARTDAY}", startDate.substring(6,8));
			newURL = StringUtils.replace(newURL, "{STARTYEAR}", startDate.substring(0,4));
		}
		
		if(StringUtils.isEmpty(endDate) == true) {
			newURL = StringUtils.replace(newURL, "{ENDMONTH}", "12");
			newURL = StringUtils.replace(newURL, "{ENDDAY}", "30");			
			newURL = StringUtils.replace(newURL, "{ENDYEAR}", "2014");
		}else {
			newURL = StringUtils.replace(newURL, "{ENDMONTH}", (Integer.parseInt(endDate.substring(4,6)) - 1) + "");
			newURL = StringUtils.replace(newURL, "{ENDDAY}",  endDate.substring(6, 8));	
			newURL = StringUtils.replace(newURL, "{ENDYEAR}",  endDate.substring(0, 4));
		}
		
		logger.info(newURL);
		request.getSession().setAttribute("yahooHistoricalData", newURL);

		String[] tickers = request.getParameterValues("tickers");
		
		if(tickers == null || tickers.length == 0 || tickers[0].length() == 0) {
			request.getSession().setAttribute("tickers", null);
			return "redirect:/tickererror.html";
		}
		
		request.getSession().setAttribute("tickers", tickers);

		return "redirect:/stock/history";
	}

	/**
	 * Looking for continuously growing
	 * 
	 * Looking for continuous 5 day growth
	 * 
	 * Looking for continuous 1 day growth over 20 dollars
	 * 
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	@RequestMapping(value = "/history", method = RequestMethod.GET)
	public void history(HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		logger.info("Calling History");

		Map<String, Double> growingTicker = new ConcurrentHashMap<String, Double>();
		Map<String, Integer> growing5DaysTicker = new ConcurrentHashMap<String, Integer>();
		Map<String, Integer> growing1DayTicker = new ConcurrentHashMap<String, Integer>();
		Map<String, Integer> growingSameDayTicker = new ConcurrentHashMap<String, Integer>();
		
		OutputStream os = response.getOutputStream();

		String[] tickers = (String[]) request.getSession().getAttribute("tickers");
		if(tickers == null) {
			os.write("Please call /parse with input parameter [tickers] i.e ?tickers=CSCO, ARMH, FSL, MXIM, INVN, QCOM, RKUS, SSNI, TEL, AAPL, GOOG \n".getBytes());
			os.flush();
			return;
		}
		
		String yahooHistoricalUrl = (String) request.getSession().getAttribute("yahooHistoricalData");
		
		for (String entry : tickers) {
			StringBuilder builder = new StringBuilder();
			BufferedReader rd = null;
			InputStreamReader is = null;
			try {
				String info = "Reading ticker + " + entry + "\n"; 
				logger.info(info);
				builder.append(info);
				String url = yahooHistoricalUrl.replace("{TICKER}", entry);
				
				logger.info("Final URL:" + url);

				HttpClient client = HttpClientBuilder.create().build();
				HttpGet httpRequest = new HttpGet(url);

				// add request header
				HttpResponse httpResponse = client.execute(httpRequest);

				is = new InputStreamReader(httpResponse.getEntity().getContent());
				rd = new BufferedReader(is);

				String line = "";

				Deque<Stock> results = new ArrayDeque<Stock>();

				// dump first line
				line = rd.readLine();

				// assuming this is read latest to oldest
				while ((line = rd.readLine()) != null) {
					String[] data = line.split(",");
					Stock stock = new Stock();
					stock.setDate(new Integer(data[0].replaceAll("-", "")));
					stock.setLow(new Double(data[1]));
					stock.setClose(new Double(data[4]));
					results.add(stock);
				}

				boolean continuousAll = true;
				int growth = 0;
				int drop = 0;

				int index = 0;

				Stock startStock = results.removeLast();
				Stock cursor = new Stock();
				cursor.setDate(startStock.getDate());
				cursor.setLow(startStock.getLow());
				cursor.setClose(startStock.getClose());

				long yesterdays =  0;
				/*
				 * determine the frequency of these growth rate
				 */
				while (!results.isEmpty()) {
					index++;
					Stock stock = results.removeLast();

					/*
					 * continuous growth over 5 days
					 */
					final long result1 = Math.round( stock.getClose() - cursor.getLow() );
					
					if (result1 > yesterdays) {
						if (index > 5) {
							info = "Continuous 5 Day Growth: $" + result1 + "\n";
							logger.info( info );
							builder.append(info);
							Integer count = growing5DaysTicker.get(entry);
							if(count == null){
								growing5DaysTicker.put(entry, 0);
							}else{
								growing5DaysTicker.put(entry, count + 1);
							}
						}
						growth ++;
						drop = 0;
						index ++;
					} else {
						index = 0;
						drop ++;
						continuousAll = false;
						growth = 0;
					}
					
					yesterdays =  result1;

					/*
					 * 30 dollars in 1 day
					 */
					final long result2 = Math.round( stock.getClose() - cursor.getLow() );
					if (result2 > 5.0) {
						info = "1 Day Growth: " + cursor.getDate() + "-" + stock.getDate() + " $" + result2 + " $" + Math.round(stock.getClose()) + "\n";
						logger.info( info );
						builder.append(info);
						Integer count = growing1DayTicker.get(entry);
						if(count == null){
							growing1DayTicker.put(entry, 0);
						}else{
							growing1DayTicker.put(entry, count + 1);
						}
					}
					
					// same day stock movement
					final long result3 = Math.round( stock.getClose() - stock.getLow() );
					if( result3 > 5.00){
						info =  "Same Day: " + stock.getDate() + " $" + result3 + " $" + Math.round(stock.getClose()) + "\n";
						logger.info( info );
						builder.append(info);
						Integer count = growingSameDayTicker.get(entry);
						if(count == null){
							growingSameDayTicker.put(entry, 0);
						}else{
							growingSameDayTicker.put(entry, count + 1);
						}
					}

					cursor = new Stock();
					cursor.setDate(stock.getDate());
					cursor.setLow(stock.getLow());
					cursor.setClose(stock.getClose());
				}

				final long result4 = Math.round( cursor.getClose() - startStock.getLow());
				info = startStock.getDate() + " " + cursor.getDate() + " Final Diff: $" + result4 + "\n";
				logger.info( info );
				builder.append(info);
				if (continuousAll) {
					info = "Continuous Growth: " + entry+ "\n";
					logger.info( info );
					builder.append(info);
					growingTicker.put(entry, new Double((growth / (growth + drop)) * 100));
				}
			} catch (Exception e) {
				String info = "Unable to query " + entry + "\n";
				logger.error( info );
				builder.append(info);
			}finally {
				if(rd != null) {
					rd.close();
				}
				if(is != null) {
					is.close();
				}
			}
			os.write(builder.toString().getBytes());
		}
		
		os.write("Done".getBytes());
		os.flush();
	}

	public class Stock {

		private Integer date;
		private Double low;
		private Double close;

		public Integer getDate() {
			return date;
		}

		public void setDate(Integer date) {
			this.date = date;
		}

		public Double getLow() {
			return low;
		}

		public void setLow(Double low) {
			this.low = low;
		}

		public Double getClose() {
			return close;
		}

		public void setClose(Double close) {
			this.close = close;
		}
		
		public String toString(){
			return date + " " + low + " " + close + "\n";
		}

	}
}
