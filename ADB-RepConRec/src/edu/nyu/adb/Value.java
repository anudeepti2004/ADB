package edu.nyu.adb;

/**
 * Every Data item maintains a list of Values.
 * The reason there are list of values for multi version read consistency 
 *
 */
public class Value implements Comparable<Value>{

	public int value; //Holds an integer value of the data item
	public int timestamp; //Maintains the timestamp when the value was committed
	
	/**
	 * Method: compareTo Compares Value object based on timestamp
	 * In order to sort a list of values, the value object should be comparable to another value object.
	 * @author Deepti Verma
	 */
	@Override
	public int compareTo(Value arg0) {
		if(arg0.timestamp < timestamp)
			return -1; 
		else if (arg0.timestamp == timestamp)
			return 0;
		else return 1;
	}
	
	/**
	 * Overriding toString method to print the value and not the value object
	 * @author Shashank
	 */
	public String toString(){
		return value+"";
	}
}
