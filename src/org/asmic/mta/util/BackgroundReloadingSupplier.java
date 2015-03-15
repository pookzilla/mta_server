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
package org.asmic.mta.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BackgroundReloadingSupplier<T> implements TimestampedSupplier<T> {

	private Object LOCK = this;

	private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
	private T cache;

	private long lastLoadTime = -1;

	public BackgroundReloadingSupplier(final Callable<T> source, int period, TimeUnit unit) {
		executor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				try {
					T result = source.call();
					synchronized (LOCK) {
						cache = result;
						lastLoadTime = System.currentTimeMillis();
						LOCK.notifyAll();
					}
				} catch (Exception e) {
				}

			}
		}, 0, period, unit);
	}

	@Override
	public T get() {
		T result = null;
		synchronized (LOCK) {
			while (cache == null) {
				try {
					LOCK.wait();
				} catch (InterruptedException e) {
				}
			}
			result = cache;
		}
		return result;
	}

	@Override
	public long getTimestamp() {
		return lastLoadTime;
	}
}
