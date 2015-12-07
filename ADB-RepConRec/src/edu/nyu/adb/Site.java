package edu.nyu.adb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import edu.nyu.adb.Lock.lockType;

public class Site {
	
	public int id; //Identifier: Different sites have different ids. In this project there are 10 sites
	public boolean isUp; //isUp is an indicator to check if the site is up or has failed. If the site is down the value is false otherwise is true
	//public final HashMap<String, DataItem> dataItems;
	public final HashMap<String, DataItem> dataItems; //Stores all committed values
	public final HashMap<String, Integer> dataItemsBufferStorage; //Buffer acts like a main memory where all non committed values are written into
	//Every site has a lock table maintained. It is hash table with the dataitem identifier as the key and 
	//the value is the array list of transactions holding locks on this dataitem (in cases of shared locks)
	//For exclusive locks there will be only 1 transaction in the array list
	public final HashMap<String,HashMap<lockType, ArrayList<Transaction>>> lockTable; 
	public final ArrayList<DataItem> availableDataItemsToRead=null; //Maintains the list of data items available to read 
	public int timestampAtWhichSiteFailed; //Maintains the timestamp at which the site failed
	public int timestampSinceItWasUp; //Maintains the timestamp at which the site was recovered or was last up
	
	/**
	 * Site  - public constructor
	 * This constructor initializes the Site fields
	 * @param idNumber : identifier to uniquely identify the Site
	 */
	public Site(int idNumber){
		id=idNumber;
		dataItems=new HashMap<>();
		isUp=true;
		lockTable=new HashMap<>();
		timestampSinceItWasUp=0;
		dataItemsBufferStorage=new HashMap<>();
	}
	
	/**
	 * Method failSite is used Simulate a site failure.
	 * @param timestamp : timestamp at which the site failed
	 * @throws Exception 
	 */
	public void failSite(int timestamp) throws Exception{
		isUp=false; //Set the indicator of whether the site is up to false
		timestampAtWhichSiteFailed=timestamp; //Set the timestamp at which it failed
		//If there are any transactions that hold locks on the dataitems in the site then abort that transaction
		ArrayList<Transaction> abortTransactionList=new ArrayList<>();
		for(String di:lockTable.keySet()){ //Looping through all the transactions
			for(lockType lt:lockTable.get(di).keySet()){
				for(Transaction t:lockTable.get(di).get(lt)){
					if(t.isRunning){
						abortTransactionList.add(t); //Aborting transactions here was causing ConcurrentModifiedException due to release of locks
					}
				}
			}
		}
		for(Transaction t2:abortTransactionList){
			t2.abort("Site "+this.id+" failed.");	//Abort transactions due to site failure
		}
		lockTable.clear(); //Clearing the lock tables in the event of a failure
	}
	
	/**
	 * Method recoverSite is used Simulate recovery of a site
	 * @param timestamp : timestamp at which the site recovers
	 */
	public void recoverSite(int timestamp){
		isUp=true; //Set the indicator to true meaning that the site now is up
		timestampSinceItWasUp=timestamp; //Setting the timestamp at which the site was up
		for(String x:dataItems.keySet()){
			//Set replicated data items 
			if(dataItems.get(x).isReplicated){
				//For all the data items in the site, if the dataitem is replicated then it is not available for read
				//it will be available for read when a write to that data item occurs
				dataItems.get(x).availablForRead=false; 
			}else{
				//Data items that are not replicated are available for read 
				dataItems.get(x).availablForRead=true;
			}
		}
	}
	
	/**
	 * Lock data item across this site
	 * @param dataItem
	 * @param newlt :type of the lock type
	 * @param t
	 * @return 	: true if it was successful to gain lock on that dataitem
	 * 			: false otherwise
	 */
	public boolean lockDataItem(String dataItem, lockType newlt, Transaction t){
		if(isUp){
			//if there is an existing lock on the data item
			if(lockTable.containsKey(dataItem)){
				HashMap<lockType, ArrayList<Transaction>> lockTypeOnDataItem=lockTable.get(dataItem);
				for(lockType ltOnDataItem:lockTypeOnDataItem.keySet()){
					//If there is a read lock on the data item and new lock is write lock then change the lock type
					if(ltOnDataItem==lockType.READ_LOCK && newlt==lockType.WRITE_LOCK){
						for(Transaction alreadyHoldingLocksTransaction:lockTypeOnDataItem.get(ltOnDataItem)){
							//if same transaction earlier had a read lock on the data item, change lock type
							if(t.transactionName.equalsIgnoreCase(alreadyHoldingLocksTransaction.transactionName)){
								HashMap<lockType, ArrayList<Transaction>> newMap=new HashMap<>();
								newMap.put(newlt, new ArrayList<Transaction>());
								newMap.get(newlt).add(t);
								lockTable.put(dataItem, newMap);
								return true;
							}else{
								return false;
							}
						}
					//if old and new lock type was a read lock
					}else if(ltOnDataItem==lockType.READ_LOCK && newlt==lockType.READ_LOCK){
						for(Transaction alreadyHoldingLocksTransaction:lockTypeOnDataItem.get(ltOnDataItem)){
							//for same transaction do nothing
							if(t.transactionName.equalsIgnoreCase(alreadyHoldingLocksTransaction.transactionName)){
								return true;
							}else{
								//for a different transaction, add lock to locktable
								HashMap<lockType, ArrayList<Transaction>> newMap=lockTable.get(dataItem);
								newMap.get(newlt).add(t);
								lockTable.put(dataItem, newMap);
								return true;
							}
						}
					//if old and new lock type is write lock 
					}else if(ltOnDataItem==lockType.WRITE_LOCK && newlt==lockType.WRITE_LOCK){
						for(Transaction alreadyHoldingLocksTransaction:lockTypeOnDataItem.get(ltOnDataItem)){
							//if write lock is required by same transaction which was earlier holding the lock return true
							if(t.transactionName.equalsIgnoreCase(alreadyHoldingLocksTransaction.transactionName)){
								return true;
							}else{
								//else return false, as write locks conflict
								return false;
							}
						}
					//if old lock on data item was write lock, and nwe lock type is read lock 
					}else if(ltOnDataItem==lockType.WRITE_LOCK && newlt==lockType.READ_LOCK){
						for(Transaction alreadyHoldingLocksTransaction:lockTypeOnDataItem.get(ltOnDataItem)){
							//if write lock was by same transaction, return true
							if(t.transactionName.equalsIgnoreCase(alreadyHoldingLocksTransaction.transactionName)){
								return true;
							}else{
								//if write lock was by another transaction, return false
								return false;
							}
						}
					}
				}
			//if there is no previous lock on the data item
			}else{
				HashMap<lockType, ArrayList<Transaction>> newMap=new HashMap<>();
				newMap.put(newlt, new ArrayList<Transaction>());
				newMap.get(newlt).add(t);
				lockTable.put(dataItem, newMap);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Method readOnlyDataItem is to simulate the multi read consistency 
	 * The data item has a list of values so compare the transaction timestamp with the timestamp of commit time of the data item
	 * @param dataItem : data item to be read
	 * @param timestamp : timestamp of start of the trasanction
	 * @return value of committed data item whose timestamp is less than the timestamp of the read only transaction  
	 */
	public Integer readOnlyDataItem(String dataItem, int timestamp){
		ArrayList<Value> diList=dataItems.get(dataItem).valueList; //Get all the values of the dataitem to be read
		Collections.sort(dataItems.get(dataItem).valueList); //Sort the values of the data item
		Integer val=null; 
		for(Value di:diList){//Looping through the list of values
			//If the committed value of data item 
			//is older than the timestamp of the start of the read only transaction then it is our value
			if(di.timestamp < timestamp){ 
				val=di.value;  
				break;
			}
		}
		return val;
	}
	
	/**
	 * Method readDataItem is used to simulate the read operation on the site for Read-Write Transactions
	 * @param dataItem : dataitem whose value the transaction wants to read
	 * @return value of data item
	 */
	public Integer readDataItem(String dataItem){
		//If the value is in the buffer (main memory) i.e. it is not yet committed then read from buffer
		if(dataItemsBufferStorage.containsKey(dataItem)){
			return dataItemsBufferStorage.get(dataItem);
		}else {
			//If the value is not present in the buffer then it is in the secondary storage which contains all the committed values
			Collections.sort(dataItems.get(dataItem).valueList);//Values will be sorted with respect to timestamp
			return dataItems.get(dataItem).valueList.get(0).value; //Return the latest committed value
		}
	}
	
	/**
	 * Method: writeDataItem is used to write data item to site
	 * This method will be called only if was able to get the lock on this site
	 * @param dataItem : data item which the transaction intends to write
	 * @param newData : New value of the data item
	 * @param timestamp : timestamp of the time when it is written
	 */
	public void writeDataItem(String dataItem, int newData, int timestamp){
		dataItemsBufferStorage.put(dataItem,newData);
	}
	
	public String toString(){
		return id+"";
	}

}
