/*
The MIT License (MIT)

Copyright (c) 2015 Kimberly Horne

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package org.asmic.mta.model;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.inject.Inject;

public class MTAStatusLoader implements Callable<StatusMap> {

	@Inject
	public MTAStatusLoader() {
	}

	private static URL url;
	private static Pattern pattern = Pattern.compile("\\[(.)\\]");
	static {
		try {
			url = new URL("http://web.mta.info/status/serviceStatus.txt");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public StatusMap call() throws Exception {
		StatusMap statusMap = new StatusMap();
		try {
			Document document = Jsoup.parse(url, 10000);
			for (Element subway : document.getElementsByTag("subway")) {
				for (Element line : subway.getElementsByTag("line")) {
					processLine(statusMap, line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return statusMap;
	}

	private void processLine(StatusMap statusMap, Element line) {
		String name = null;
		String status = null;
		Document text = null;
		String processedText = null;
		for (Element element : line.getAllElements()) {
			String content = element.text();
			switch (element.nodeName()) {
			case "name":
				name = content;
				break;

			case "status":
				status = content;
				break;

			case "text":
				text = Jsoup.parse(content);
				processedText = processHtml(text).replaceAll("[^\\p{ASCII}]", " ");
			}
		}

		if (name != null) {
			Matcher matcher = pattern.matcher(processedText);

			int index = 0;
			while (matcher.find(index)) {
				String match = matcher.group(1);
				index = matcher.end();
				Map<String, String> byStatus = statusMap.get(match);
				if (byStatus == null) {
					byStatus = new HashMap<>();
					statusMap.put(match, byStatus);
				}
				byStatus.put(status.toLowerCase(), processedText);
			}
		}
	}

	private String processHtml(Document text) {
		StringBuffer buffer = new StringBuffer();
		for (org.jsoup.nodes.Element body : text.getElementsByTag("body")) {
			for (org.jsoup.nodes.Element child : body.children()) {
				if ("p".equals(child.nodeName())) {
					buffer.append(child.text());
				}
			}
		}
		return buffer.toString();
	}
}
