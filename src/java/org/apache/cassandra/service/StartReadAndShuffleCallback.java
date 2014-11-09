package org.apache.cassandra.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cassandra.db.Row;
import org.apache.cassandra.db.SendLocalReadResponse;
import org.apache.cassandra.db.StartReadAndShuffleResponse;
import org.apache.cassandra.net.IAsyncCallback;
import org.apache.cassandra.net.MessageIn;
import org.apache.cassandra.utils.concurrent.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartReadAndShuffleCallback<TMessage, TResolved> implements IAsyncCallback<TMessage>{

	protected static final Logger logger = LoggerFactory.getLogger(StartReadAndShuffleCallback.class);
	
	private final AtomicInteger accepts = new AtomicInteger(0);
	private final int waitFor;
	private final SimpleCondition condition = new SimpleCondition();
	private final long timeout = 1;

	private final AtomicInteger rowApts = new AtomicInteger(0);	
	private final SimpleCondition rowCond = new SimpleCondition();
	private List<Row> finalRows = new ArrayList<Row>();
	
	public StartReadAndShuffleCallback(int waits){
		this.waitFor = waits;
	}
	
	@Override
	public void response(MessageIn<TMessage> msg) {
		logger.info("@daidong debug: response from StartReadAndShuffleCallback");
		
		StartReadAndShuffleResponse response = (StartReadAndShuffleResponse) msg.payload;
		if (response.status() ==2){
			// we need to aggregate all the rows from different servers
			rowApts.incrementAndGet();
			finalRows.addAll(response.rows());

			if (rowApts.get() >= waitFor){
				rowCond.signalAll();
			}
		} else {
			if (response.status() == 0 || response.status() == 1)
				accepts.incrementAndGet();
			if (accepts.get() >= waitFor){
				condition.signalAll();
			}
		}
	}

	public boolean awaitRows(long time){
		try {
			return rowCond.await(time, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			throw new AssertionError(ex);
		}
	}
	
	public List<Row> getRows(){
		if (!awaitRows(this.timeout)){
			throw new AssertionError("Time out exception");
		}
		return finalRows;
	}
	
	public boolean await(long time){
		try {
			return condition.await(time, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			throw new AssertionError(ex);
		}
	}
	
	public int get(){
		if (!await(this.timeout)){
			throw new AssertionError("Time out exception");
		}
		return accepts.get();
	}
	
	@Override
	public boolean isLatencyForSnitch() {
		return true;
	}

}