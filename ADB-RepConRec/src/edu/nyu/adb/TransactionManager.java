package edu.nyu.adb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;


public class TransactionManager {
	private static BufferedReader br;
	private static BufferedWriter bw;
	private static TransactionManager instance;
	private static ArrayList<Transaction> transactionList;
	private static Queue<Transaction> transactionWaitingList;
	private static boolean anyTransactionsWaiting=false;
	private static HashMap<String,Transaction> transactionMap;
	private int currentTimeStamp=0;
	private static ArrayList<Site> siteList;
	
	/**
	 * Initializes Transaction manager
	 * @param in
	 * @param out
	 * @author Deepti Verma
	 */
	public static void init(InputStream in, OutputStream out) {
		 BufferedInputStream bi=new BufferedInputStream(in);
		 br=new BufferedReader(new InputStreamReader(bi));
		 BufferedOutputStream bo=new BufferedOutputStream(out);
		 bw=new BufferedWriter(new OutputStreamWriter(bo) );
		 transactionList=new ArrayList<Transaction>();
		 transactionMap=new HashMap<>();
		 transactionWaitingList=new ArrayDeque<>();
		 siteList=new ArrayList<>();
	}
	
	
	/**
	 * Singleton
	 * @return the only instance of the transaction manager
	 * @author Shashank
	 */
	public static TransactionManager getInstance(){
		if(instance==null){
			instance=new TransactionManager();
		}
		return instance;
	}
	
	/**
	 * Read input commands and execute transactions
	 * @throws Exception
	 * @author Deepti Verma
	 */
	public void run() throws Exception{
		String line=null;
		//Read and execute input operations
		while((line=br.readLine())!=null){
			line=line.trim();
			if(!(line.startsWith("\\") || line.isEmpty())){ //Ignore a line that starts with \\ then it is comment or is empty
				String[] operations=line.split(";");
				for(int i=0;i< operations.length;i++){
					operations[i]=operations[i].trim();
					doOperation(operations[i]);
				}
			}
			
			//Check for transactions waiting to be executed
			Queue<Transaction> tempQueue=new ArrayDeque<>();
			while(!transactionWaitingList.isEmpty()){
				Transaction tr=transactionWaitingList.poll();
				if(tr.isWaiting && !tempQueue.contains(tr)){
					if(!doOperation(tr.operationWaiting)){
						anyTransactionsWaiting=true;
						tempQueue.add(tr);
					}else{
						tr.isWaiting=false;
					}
				}
			}
			if(!tempQueue.isEmpty())
				transactionWaitingList.addAll(tempQueue);
			currentTimeStamp++;
		}
		bw.flush();
	}
	
	/**
	 * Execute operations of transactions
	 * @param operation : specifies what operation to perform like begin, read, write, dump, end etc
	 * @return true- if operation was successfully executed, otherwise false
	 * @throws Exception
	 * @author Deepti Verma
	 */
	private boolean doOperation(String operation) throws Exception {
		//Start Read-only transaction
		if(operation.startsWith("beginRO(")){
			//Get the name of the transaction
			String transactionName=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();	//extract transaction name
			//Create a new transaction
			Transaction t=new Transaction(transactionName,currentTimeStamp,bw);		
			t.readonlyTransaction=true; //Set that it is a read only transaction
			transactionMap.put(transactionName, t); //Add entry to transaction map
			transactionList.add(t); //Add transaction to the transaction list
			return true; //Successfully created a new read only transaction
		}
		//Start Read-write transaction
		else if(operation.startsWith("begin(")){ //All read-write operations start with begin keyword
			String transactionName=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();
			Transaction t=new Transaction(transactionName,currentTimeStamp,bw); //Creating a new transaction
			transactionMap.put(transactionName, t);
			transactionList.add(t);
			return true;
		}
		//Execute Read operation
		else if(operation.startsWith("R(")){
			String transactionName=operation.substring(operation.indexOf("(")+1, operation.indexOf(",")).trim();
			Transaction t=transactionMap.get(transactionName); //Get the transaction object belonging to the given transaction in the operation
			if(t.isRunning){ //Check if the transaction is still running, if it is aborted then there is no point performing any operations basically ignoring the transaction
				String dataItem=operation.substring(operation.indexOf(",")+1, operation.indexOf(")")).trim(); //Get the data item intended to be read
				t.operationsList.add(operation); //Add the operation to the list maintained by the transaction
				Integer val=readDataItem(t,dataItem); //Perform the read operation
				//if data item which is read null; put operation in waiting for transaction
				if(val==null){ //Transaction might be waiting
					t.operationWaiting=operation;
					anyTransactionsWaiting=true;
					return false; //Operation was not successfully performed
				}else
					//If data item has a value, display read value
					bw.write(dataItem +": "+val+"\n"); //Print the output
			}
			bw.flush();
			return true; //If the control reached this point then the operation was succesful
		}
		//Execute write operation 
		else if(operation.startsWith("W(")){
			String transactionName=operation.substring(operation.indexOf("(")+1, operation.indexOf(",")).trim();
			Transaction t=transactionMap.get(transactionName); //Get the transaction object
			if(t.isRunning){ //Check if the operation has not yet aborted
				String dataItem=operation.substring(operation.indexOf(",")+1, operation.lastIndexOf(",")).trim();
				int value=Integer.parseInt(operation.substring(operation.lastIndexOf(",")+1, operation.indexOf(")")).trim()); //Perform the write operations
				writeToAllSites(t,dataItem,value,operation);
				//Check if this should be within t.isrunning condition
				if(t.isWaiting)
					return false;
			}
			return true;
		//Execute Dump data operation
		}else if(operation.startsWith("dump")){
			if(operation.contains("dump()")){
				dump();
			}else if(operation.startsWith("dump(x")){
				String dataItem=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();
				dump(dataItem);
			}else {
				int siteId=Integer.parseInt(operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim());
				dump(siteId);
			}
			bw.flush();
			return true;
		//End a transaction
		}else if(operation.startsWith("end(")){
			String transactionName=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();
			Transaction t=transactionMap.get(transactionName);
			endTransaction(t); //Perform the end transaction
			return true;
		//Shutdown a site  
		}else if(operation.startsWith("fail(")){
			String siteName=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();
			siteList.get(Integer.parseInt(siteName)-1).failSite(currentTimeStamp);//Perform a fail site operation
			return true;
		//Recover a site
		}else if(operation.startsWith("recover(")){
			String siteName=operation.substring(operation.indexOf("(")+1, operation.indexOf(")")).trim();
			siteList.get(Integer.parseInt(siteName)-1).recoverSite(currentTimeStamp); //Perform a recovery of site
			return true;
		//Omit comments  
		}else if(operation.startsWith("\\")||operation.isEmpty()){ //If the lines in the put are comments or are empty then ignore that line
			//Do nothing
		}
		return false;
	}
	
	/**
	 * Get value of data item 
	 * @param t
	 * @param dataItem
	 * @return Value of data item 
	 * @throws Exception
	 * @author Deepti Verma 
	 */
	private Integer readDataItem(Transaction t, String dataItem) throws Exception {
		Site availableSite=getAvailableSiteToReadFrom(dataItem); //Get sites where the dataitem is present
		if(availableSite == null){ 
			t.isWaiting=true; //There are no sites up having that dataitem then transaction needs to wait until the site is up
			transactionWaitingList.add(t);
			return null;
		}
		// Read only data items do not need locks
		if(t.readonlyTransaction){
			return availableSite.readOnlyDataItem(dataItem, t.transactionStartTimestamp);
		}else{
			//Obtain read-only lock and read data item value
			if(t.getLock(availableSite,dataItem, Site.lockType.READ_LOCK)){
				return availableSite.readDataItem(dataItem);
			}else{
				//if we are unable to obtain read only lock add transaction to waiting list
				t.isWaiting=true;
				transactionWaitingList.add(t);
				for(Transaction t1:transactionList){ //check if transaction should be executed according to wait die protocol
					if(!t1.transactionName.equalsIgnoreCase(t.transactionName) && t1.lockOnDataItems.contains(dataItem)){
						if(t1.transactionStartTimestamp < t.transactionStartTimestamp){
							t.abort("Transaction "+t1.transactionName+" is older than "+t.transactionName);
						}else{
							t.isWaiting=true;
							transactionWaitingList.add(t);
							anyTransactionsWaiting=true;
						}
						//break;
					}
				}
				return null;
			}
		}
	}
	
	/**
	 * 
	 * @param dataItem
	 * @return site from which the data item can be read
	 * @author Shashank
	 */
	private Site getAvailableSiteToReadFrom(String dataItem) {
		ArrayList<Site> siteList=getSitesContainingAvailableDataitem(dataItem);
		for(Site site:siteList){
			if(site.isUp){
				return site;
			}
		}
		return null;
	}
	
	/**
	 * 
	 * @param dataItem
	 * @return list of sites containing the data item available for read 
	 */
	private ArrayList<Site> getSitesContainingAvailableDataitem(String dataItem) {
		ArrayList<Site> sites=new ArrayList<>();
		for(Site site:siteList){
			if(site.dataItems.containsKey(dataItem)){
				if(site.dataItems.get(dataItem).availablForRead)
					sites.add(site);
			}
		}
		return sites;
	}
	
	/**
	 * End transaction 
	 * @param t - transaction to be ended
	 * @throws Exception
	 * @author Shashank 
	 */
	private void endTransaction(Transaction t) throws Exception {
		if(t!=null)
		{
			if(t.isRunning)
			{
				if(t.isWaiting){ //Abort Transaction if not all sites have been up since it accessed dataitems
					t.abort("Operations still waiting");
					//System.out.println("Aborting transaction "+t.transactionName);
				}else{
					t.commit(currentTimeStamp);
					//System.out.println("Commiting transaction "+t.transactionName);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param dataitem
	 * @return a list of sites containing a data item
	 * @author Deepti Verma
	 */
	public ArrayList<Site> getSitesContainingDataitem(String dataitem) {
		ArrayList<Site> sites=new ArrayList<>();
		for(Site site:siteList){
			if(site.dataItems.containsKey(dataitem) && site.isUp){
				sites.add(site);
			}
		}
		return sites;
	}
	
	/**
	 * Execute write to all sites for a data item by a transaction 
	 * @param t: transaction that issued the write command
	 * @param dataItem : data item whose new value is to be written
	 * @param value: new value of the data item
	 * @param operation : write command
	 * @throws Exception
	 * @author Deepti Verma
	 */
	private void writeToAllSites(Transaction t, String dataItem, int value,String operation) throws Exception {
		/*if(t.lockOnDataItems.contains(dataItem)){
			for(Site s:SiteManager.siteList){
				s.writeDataItem(dataItem, value,currentTimeStamp);
			}
		}else{*/
			boolean gettingLockSuccessful=t.getLock(dataItem, Site.lockType.WRITE_LOCK);	//obtain lock on data item
			if(gettingLockSuccessful){		//if a lock is obtained successfully, write on each site
				ArrayList<Site> sites=getSitesContainingDataitem(dataItem);		//Get all sites containing the data item
				if(sites.isEmpty()){ //No sites are up to get a lock
					t.isWaiting=true; 
					transactionWaitingList.add(t);  //Add transactions to the waiting list
					t.operationWaiting=operation; //After the transaction is set to waiting it cannot accept anymore operations so just set the filed operation waiting to the operation 
					anyTransactionsWaiting=true; //Indicate that the transactions are waiting
				}else{
					t.isWaiting=false; //If getting lock was successful then the transaction need not wait
					for(Site s:sites){ 
						s.writeDataItem(dataItem, value,currentTimeStamp); //Write on all sites (written first to buffer and then to secondary storage at commit time)
					}
				}
			}else{ //If getting locks on the data item was unsuccessful then check for transactions holding locks on that data item	
				//Else, check for transactions older than this transaction and decide if abort has to be executed according to wait-die protocol 
					for(Transaction t1:transactionList){//Loop through every transaction in the transaction list which has a lock on that data item
						if(!t1.transactionName.equalsIgnoreCase(t.transactionName) && t1.lockOnDataItems.contains(dataItem)){
							//If the transaction in transaction list having lock on the data item is older than the transaction trying to wrtie then abort the transaction
							if(t1.transactionStartTimestamp < t.transactionStartTimestamp){ 
								t.abort("because of wait-die. Transaction "+t1.transactionName+" is older than "+t.transactionName);
							}else{	//put transaction in waiting if there is no older transaction holding a lock on the data item
								//t1.abort("Transaction "+t.transactionName+" is older than "+t1.transactionName);
								t.isWaiting=true; //Transaction needs to wait as it is older than the transaction holding the lock on the data item
								transactionWaitingList.add(t);  //Add transactions to the waiting list
								t.operationWaiting=operation; //After the transaction is set to waiting it cannot accept anymore operations so just set the filed operation waiting to the operation 
								anyTransactionsWaiting=true; //Indicate that the transactions are waiting
								//writeToAllSites(t,dataItem,value);
							}
							//break;
						}
					}
				}
			
		}
		
	/**
	 * Show data items on a site
	 * @param siteId
	 * @throws IOException
	 */
	private void dump(int siteId) throws IOException {
		Site s=siteList.get(siteId-1);
		bw.write("Site : "+s.id);
		bw.write("\n");
		for(String x:sortKeys(s.dataItems.keySet())){
			bw.write(x);
			bw.write(" : ");
			Collections.sort(s.dataItems.get(x).valueList);
			bw.write(s.dataItems.get(x).valueList.get(0).toString());
			bw.write("\n");
		}
		bw.flush();
	}
	
	/**
	 * Sorts the variables in the map
	 * @param keySet
	 * @return
	 */
	private ArrayList<String> sortKeys(Set<String> keySet) {
		ArrayList<Integer> keys=new ArrayList<>();
		for(String k:keySet){
			keys.add(Integer.parseInt(k.substring(1)));
		}
		Collections.sort(keys);
		ArrayList<String> sortedKeys=new ArrayList<>();
		for(Integer p:keys){
			sortedKeys.add("x"+p);
		}
		return sortedKeys;
	}


	/**
	 * Show value of a data item on all sites 
	 * @param dataItem
	 * @throws Exception
	 */
	private void dump(String dataItem) throws Exception{
		for(Site site:siteList){
			if(site.dataItems.containsKey(dataItem)){
				bw.write("Site :"+site.id);
				bw.write("\n");
				bw.write(dataItem);
				bw.write(" : ");
				Collections.sort(site.dataItems.get(dataItem).valueList);
				bw.write(site.dataItems.get(dataItem).valueList.get(0).toString());
				bw.write("\n");
			}
		}
		bw.flush();
	}
	
	/**
	 * Show all data items on all sites
	 * @throws IOException
	 */
	private void dump() throws IOException {
		for(Site site:siteList){
			dump(site.id);
			bw.write("\n");
		}
		bw.flush();
	}
	
	/**
	 * Add a new site to the site list to the transaction manager
	 * @param Site s: to be added to the transaction managers site list
	 * @author Shashank
	 */
	public void addSite(Site s){
		siteList.add(s);
	}
}
