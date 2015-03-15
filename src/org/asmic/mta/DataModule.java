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
package org.asmic.mta;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.asmic.mta.dko.mta.StopTimes;
import org.asmic.mta.model.FeedLoader;
import org.asmic.mta.model.MTAStatusLoader;
import org.asmic.mta.model.StatusMap;
import org.asmic.mta.util.BackgroundReloadingSupplier;
import org.asmic.mta.util.TimestampedSupplier;
import org.hsqldb.jdbc.JDBCDataSource;
import org.kered.dko.Context;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;

public class DataModule extends AbstractModule {

	private String dbPath;
	private String apiKey;

	public DataModule(String dbPath, String apiKey) {
		this.dbPath = dbPath;
		this.apiKey = apiKey;
	}

	@Override
	protected void configure() {
		Context.getVMContext().setDataSource(getDataSource()).setAutoUndo(false);
		// prime the DB connection.
		StopTimes.ALL.first(); 
	}

	@Provides
	protected TimestampedSupplier<StatusMap> getStatusMapSupplier() {
		BackgroundReloadingSupplier<StatusMap> supplier = new BackgroundReloadingSupplier<StatusMap>(
				new MTAStatusLoader(), 1, TimeUnit.MINUTES);
		return supplier;
	}

	@Provides
	protected TimestampedSupplier<Map<String, TripUpdate>> getFeedMessageSupplier() {
		BackgroundReloadingSupplier<Map<String, TripUpdate>> supplier = new BackgroundReloadingSupplier<Map<String, TripUpdate>>(
				new FeedLoader(apiKey), 1, TimeUnit.MINUTES);
		return supplier;
	}
	
	@Provides
	protected DataSource getDataSource() {
		JDBCDataSource source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:file:" + dbPath + ";ifexists=true");
		source.setUser("SA");
		return source;
	}
}
