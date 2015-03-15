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

import org.asmic.mta.dko.mta.StopTimes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public class Arrival {
	@JsonProperty
	private String stopId;
	@JsonProperty
	private long scheduledArrivalTime;
	@JsonProperty
	private Long projectedArrivalTime;
	@JsonProperty
	private String headsign;
	@JsonIgnore
	private Integer directionId;

	public Arrival(StopTimes input, long timeRelativeTo) {
		this.stopId = input.getStopId();
		this.scheduledArrivalTime = timeRelativeTo + input.getArrivalTime() * 1000;
	}

	public String getStopId() {
		return stopId;
	}

	public void setStopId(String stopId) {
		this.stopId = stopId;
	}

	public long getScheduledArrivalTime() {
		return scheduledArrivalTime;
	}

	public void setScheduledArrivalTime(long scheduledArrivalTime) {
		this.scheduledArrivalTime = scheduledArrivalTime;
	}

	@JsonInclude(Include.NON_NULL)
	public Long getProjectedArrivalTime() {
		return projectedArrivalTime;
	}

	public void setProjectedArrivalTime(long projectedArrivalTime) {
		this.projectedArrivalTime = projectedArrivalTime;
	}

	public void setHeadsign(String headsign) {
		this.headsign = headsign;
	}

	public void setDirection(Integer directionId) {
		this.directionId = directionId;
	}

	@JsonProperty("direction")
	public String getDirectionString() {
		return directionId == 0 ? "northbound" : "southbound";
	}
}
