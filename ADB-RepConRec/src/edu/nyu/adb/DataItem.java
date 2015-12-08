package edu.nyu.adb;

import java.util.ArrayList;

public class DataItem  {

	public String dataIdentifier; //refers to the data item. For example: x1,x3
	public boolean isReplicated; //it is an indicator to check if the dataidentifier is replicated
	public boolean availablForRead; //In an event of a site failing to keep a track if this data item is available for read or not after recovery
	public ArrayList<Value> valueList; //Value list sorted by timestamp for multi-read consistency
	
	/**
	 * Data Item Constructor
	 * Initializes the value list 
	 * @author Shashank
	 */
	public DataItem(){
		valueList=new ArrayList<>();
	}
	
	/**
	 * Data Item Overloaded Constructor
	 * Initializes the value list, dataidentifier, is Replicated and availableForRead
	 * @author Shashank 
	 */
	public DataItem(String didentifier,boolean isR, boolean aR){
		dataIdentifier=didentifier;
		isReplicated=isR;
		availablForRead=aR;
		valueList=new ArrayList<>();
	}
}
