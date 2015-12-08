package edu.nyu.adb;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Transaction {

	//public int transactionId;
	public String transactionName;
	public int transactionStartTimestamp;
	public boolean isWaiting;
	public boolean isRunning;
	public BufferedWriter bw;
	public ArrayList<String> operationsList;
	public boolean readonlyTransaction=false;
	public ArrayList<String> lockOnDataItems;
	public ArrayList<Site> sitesAccessed;
	public String operationWaiting;
 	
	/**
	 * Constructor for transaction
	 * @param name
	 * @param timestamp
	 * @param bwTM 
	 * @author Shashank
	 */
	public Transaction(String name,int timestamp, BufferedWriter bwTM){
		transactionStartTimestamp=timestamp;
		transactionName=name;
		lockOnDataItems=new ArrayList<>();
		operationsList=new ArrayList<>();
		sitesAccessed=new ArrayList<>();
		isWaiting=false;
		isRunning=true;
		bw=bwTM;
	}

	/**
	 * Obtain lock on data item on all sites
	 * @param dataitem
	 * @param l
	 * @return true - if lock is obtained successfully
	 * @author Deepti Verma
	 */
	public boolean getLock(String dataitem, Site.lockType l) {
		ArrayList<Site> sitesContainingDataitem=TransactionManager.getInstance().getSitesContainingDataitem(dataitem);//Get all sites containing the data item
		boolean successfullyAcquiredAllLocks=true;
		//get lock on data item on all sites
		//TODO: What happens if you can't get a lock on data item on one site..is it possible?
		for(Site s: sitesContainingDataitem){
				if(!getLock(s, dataitem,l)){
					successfullyAcquiredAllLocks=false;
				}
		}
		//Add locked data item to list 
		if(successfullyAcquiredAllLocks){
			if(!lockOnDataItems.contains(dataitem))
				lockOnDataItems.add(dataitem);
		}
		return successfullyAcquiredAllLocks;
	}
	
	/**
	 * Obtain lock on data item on a particular site
	 * @param siteToReadFrom
	 * @param dataitem
	 * @param l
	 * @return true - if lock is obtained successfully
	 * @author Deepti Verma
	 */
	public boolean getLock(Site siteToReadFrom,String dataitem, Site.lockType l) {
		boolean successfullyAcquiredAllLocks=true;
		//	Obtain lock on data item if site is running
		if(siteToReadFrom.isUp){
				if(!siteToReadFrom.lockDataItem(dataitem, l,this)){
					successfullyAcquiredAllLocks=false;
				}else{
					if(!sitesAccessed.contains(siteToReadFrom))
						sitesAccessed.add(siteToReadFrom);
				}
			}
		if(successfullyAcquiredAllLocks){
			if(!lockOnDataItems.contains(dataitem))
				lockOnDataItems.add(dataitem);
		}
		return successfullyAcquiredAllLocks;
	}
	
	/**
	 * Method abort simulates aborting a transaction and clearing the locks held by that transaction
	 * Abort a transaction with the reason of why it aborts
	 * @param whyMessage
	 * @throws IOException
	 * @author Deepti Verma 
	 */
	public void abort(String whyMessage) throws IOException{
		for(String dataItem:lockOnDataItems){
			ArrayList<Site> sitesAccessed=TransactionManager.getInstance().getSitesContainingDataitem(dataItem);
			for(Site s: sitesAccessed){
				s.dataItemsBufferStorage.remove(dataItem); // If the transaction wrote a value to a site clear the buffer for that dataitem
			}
		}
		releaseLocks(); //Release the locks held by the transaction
		isRunning=false; //Set that the transaction is not running anymore
		bw.write("Aborting transaction "+transactionName+" because "+whyMessage+"\n");
		bw.flush();
	}

	/**
	 * Release locks on all data items
	 * When a transaction ends whether it aborts or commits it will release locks on the data items that the transaction holds
	 * @author Deepti Verma
	 */
	public void releaseLocks() {
		for(String dataitem:lockOnDataItems){ //Loop through all the data items it has locks on
			for(Site s: sitesAccessed){
				boolean removeDataItem=false;
				if(s.lockTable.containsKey(dataitem)){
						for(Site.lockType lt:s.lockTable.get(dataitem).keySet()){
							s.lockTable.get(dataitem).get(lt).remove(this);
							if(s.lockTable.get(dataitem).get(lt).isEmpty()){
								removeDataItem=true;
							}
						}
				if(removeDataItem)
					s.lockTable.remove(dataitem); //Remove the locks from the lock table
				}
			}
		}
	}

	/**
	 * Commit a transaction
	 * @param currentTtimestamp : commit timestamp. All the write values in the buffer will be populated at the commit time
	 * @throws IOException
	 * @author Deepti Verma 
	 */
	public void commit(int currentTtimestamp) throws IOException {
		for(String dataItem:lockOnDataItems){
			for(Site s: sitesAccessed){
				if(!s.dataItemsBufferStorage.isEmpty()&& s.dataItems.containsKey(dataItem) && s.dataItemsBufferStorage.containsKey(dataItem)){
					DataItem dItemFromSecStorage=s.dataItems.get(dataItem);
					ArrayList<Value> dataItemValueListSS=dItemFromSecStorage.valueList;
					dItemFromSecStorage.availablForRead=true;
					Value v=new Value();
					v.value=s.dataItemsBufferStorage.get(dataItem);
					v.timestamp=currentTtimestamp; //Commit timestamp
					dataItemValueListSS.add(v);
					s.dataItemsBufferStorage.remove(dataItem);
				}
			}
		}
		//release locks after commits
		releaseLocks();
		isRunning=false;
		bw.write("Commiting transaction "+transactionName+"\n");
		bw.flush();
	}
}
