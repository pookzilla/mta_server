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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.asmic.mta.dko.mta.Calendar;
import org.asmic.mta.dko.mta.StopTimes;
import org.asmic.mta.dko.mta.Stops;
import org.asmic.mta.dko.mta.Trips;
import org.asmic.mta.model.Arrival;
import org.asmic.mta.model.Directions;
import org.asmic.mta.model.Line;
import org.asmic.mta.model.StatusMap;
import org.asmic.mta.util.TimestampedSupplier;
import org.kered.dko.Condition;
import org.kered.dko.Field;
import org.kered.dko.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;

@Path("/rest")
@Singleton
public class MTAResource {

	final private TimestampedSupplier<StatusMap> statusSupplier;
	final private TimestampedSupplier<Map<String, TripUpdate>> realtimeData;
	final Logger logger = LoggerFactory.getLogger(MTAResource.class);

	@Inject
	public MTAResource(TimestampedSupplier<StatusMap> statusSupplier,
			TimestampedSupplier<Map<String, TripUpdate>> realtimeData) {
		this.statusSupplier = statusSupplier;
		this.realtimeData = realtimeData;
	}
	
	private void logRequest(String endpoint, HttpServletRequest req) {
		logger.info(endpoint + " request " + req.getRemoteAddr());
	}

	@GET
	@Path("/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getStatuses(@Context HttpServletRequest req) {
		Map<String, Object> result = new HashMap<>();
		result.put("results", statusSupplier.get());
		result.put("statusTimestamp", statusSupplier.getTimestamp());
		logRequest("/status", req);
		return result;
	}

	@GET
	@Path("/arrivals")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Line> getArrivalsForStation(@Context HttpServletRequest req, @QueryParam("station") final String stationName,
			@QueryParam("line") final List<String> lines, final @QueryParam("date") Long longTime,
			@QueryParam("direction") final String direction) {

		final Date date = longTime == null ? new Date() : new Date(longTime);

		final java.util.Calendar calendar = java.util.Calendar.getInstance(Locale.US);
		calendar.setTime(date);

		final List<String> serviceIds = getServiceIds(calendar);

		final Map<String, Line> result = new TreeMap<String, Line>();
		final List<String> stopIds = Stops.ALL.where(Stops.STOP_NAME.eq(stationName)).asList(Stops.STOP_ID);

		Map<String, TripUpdate> realtimeMap = realtimeData.get();
		long realtimeTimestamp = realtimeData.getTimestamp();
		for (String line : lines) {
			final List<Arrival> transformedArrivals = processArrivals(realtimeMap, direction, calendar, serviceIds,
					stopIds, line);
			int hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY);
			int minuteOfHour = calendar.get(java.util.Calendar.MINUTE);
			if (hourOfDay >= 23 && minuteOfHour >= 30) {
				// we're close to the next day - better include the next days
				// results as well.
				// not sure there's a more elegant way of shifting all these
				// values. Need to account for end of week, end of month, end of
				// year, etc...
				Date tomorrow = new Date(date.getTime() + 30 * 60 * 1000);
				calendar.setTime(tomorrow);
				calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
				calendar.set(java.util.Calendar.MINUTE, 0);
				calendar.set(java.util.Calendar.SECOND, 0);
				calendar.set(java.util.Calendar.MILLISECOND, 0);

				List<Arrival> moreArrivals = processArrivals(realtimeMap, direction, calendar, serviceIds, stopIds,
						line);
				transformedArrivals.addAll(moreArrivals);
			}

			final Line lineResult = new Line(statusSupplier.get(), statusSupplier.getTimestamp(), realtimeTimestamp, System.currentTimeMillis());
			lineResult.setLine(line);
			lineResult.setArrivals(transformedArrivals);
			result.put(line, lineResult);
		}
		logRequest("/arrivals", req);
		return result;
	}

	private List<Arrival> processArrivals(Map<String, TripUpdate> realtimeMap, final String direction,
			final java.util.Calendar calendar, final List<String> serviceIds, final List<String> stopIds, String line) {
		final int currentTimeInSeconds = getSecondsOfDay(calendar);
		// should probably adjust this downward if we're pulling results from
		// the next day
		final int endTime = currentTimeInSeconds + 3600;
		final List<Join<StopTimes, Trips>> queryResults = StopTimes.ALL
				.leftJoin(Trips.class, StopTimes.TRIP_ID.eq(Trips.TRIP_ID))
				.where(Trips.ROUTE_ID.eq(line).and(Trips.SERVICE_ID.in(serviceIds), StopTimes.STOP_ID.in(stopIds),
						StopTimes.ARRIVAL_TIME.gt(currentTimeInSeconds), StopTimes.ARRIVAL_TIME.lt(endTime))).asList();

		final List<Arrival> transformedArrivals = new ArrayList<>(queryResults.size());
		final long startOfDay = getStartOfDay(calendar);
		for (Join<StopTimes, Trips> input : queryResults) {
			final StopTimes stopTime = input.l;
			final Trips trip = input.r;
			final Arrival arrival = new Arrival(stopTime, startOfDay);
			if (direction != null && !direction.equalsIgnoreCase(Directions.values()[trip.getDirectionId()].toString())) {
				continue;
			}
			arrival.setHeadsign(trip.getTripHeadsign());
			arrival.setDirection(trip.getDirectionId());

			/**
			 * Check the realtime data for an update. You need to accomodate for
			 * the difference of ids tho - the tripID in the realtime data is
			 * the tripID in the static minus the service ID.
			 */
			final String modifiedTripId = trip.getTripId().substring(trip.getServiceId().length() + 1);

			final TripUpdate update = realtimeMap.get(modifiedTripId);
			if (update != null) {
				for (StopTimeUpdate stopTimeUpdate : update.getStopTimeUpdateList()) {
					if (stopTimeUpdate.getStopId().equals(stopTime.getStopId())) {
						arrival.setProjectedArrivalTime(stopTimeUpdate.getArrival().getTime() * 1000);
					}
				}
			}
			transformedArrivals.add(arrival);
		}
		return transformedArrivals;
	}

	private long getStartOfDay(java.util.Calendar original) {
		final java.util.Calendar calendar = (java.util.Calendar) original.clone();
		calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
		calendar.set(java.util.Calendar.MINUTE, 0);
		calendar.set(java.util.Calendar.SECOND, 0);
		calendar.set(java.util.Calendar.MILLISECOND, 0);
		return calendar.getTimeInMillis();
	}

	private int getSecondsOfDay(java.util.Calendar calendar) {
		final int hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY);
		final int minuteOfHour = calendar.get(java.util.Calendar.MINUTE);
		final int secondOfMinute = calendar.get(java.util.Calendar.SECOND);

		return secondOfMinute + (60 * minuteOfHour) + (3600 * hourOfDay);
	}

	private List<String> getServiceIds(java.util.Calendar calendar) {
		final int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
		final Condition condition = getCalendarConditonForDayOfWeek(dayOfWeek);
		return Calendar.ALL.where(condition).asList(Calendar.SERVICE_ID);
	}

	private Condition getCalendarConditonForDayOfWeek(int dayOfWeek) {
		Field<Boolean> field = null;
		switch (dayOfWeek) {
		case java.util.Calendar.SUNDAY:
			field = Calendar.SUNDAY;
			break;
		case java.util.Calendar.MONDAY:
			field = Calendar.MONDAY;
			break;
		case java.util.Calendar.TUESDAY:
			field = Calendar.TUESDAY;
			break;
		case java.util.Calendar.WEDNESDAY:
			field = Calendar.WEDNESDAY;
			break;
		case java.util.Calendar.THURSDAY:
			field = Calendar.THURSDAY;
			break;
		case java.util.Calendar.FRIDAY:
			field = Calendar.FRIDAY;
			break;
		case java.util.Calendar.SATURDAY:
			field = Calendar.SATURDAY;
			break;
		}
		return field.eq(true);
	}

	@GET
	@Path("/stationDetails")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getStationDetails(@Context HttpServletRequest req, @QueryParam("station") String stationName) {
		final List<String> stopIds = Stops.ALL.where(Stops.STOP_NAME.eq(stationName)).asList(Stops.STOP_ID);

		final List<String> trips = Trips.ALL.with(Trips.FK_TRIP_STOP_TIMES).where(StopTimes.STOP_ID.in(stopIds))
				.onlyFields(Trips.ROUTE_ID).groupBy(Trips.ROUTE_ID).asList(Trips.ROUTE_ID);
		
		logRequest("/stationDetails", req);
		return trips;
	}

	@GET
	@Path("/stations")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getStopsLike(@Context HttpServletRequest req, @QueryParam("like") String nameLike) {
		logRequest("/stations", req);
		return Stops.ALL.where(Stops.STOP_NAME.like("%" + nameLike + "%")).groupBy(Stops.STOP_NAME)
				.onlyFields(Stops.STOP_NAME).asList(Stops.STOP_NAME);
	}
}