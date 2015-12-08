package edu.nyu.adb;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainProgram {
	/**
	 * Entry point of the application
	 * @param args : command line arguments
	 * @throws Exception if the initialization fails or transaction manager fails
	 * @author Deepti Verma
	 */
	public static void main(String[] args) throws Exception{
		if(args.length > 0){
			if(args.length == 1){
				init(new FileInputStream(args[0]),System.out);//Input file supplied but no output file
			}else{
				//Input file and output file supplied 
				init(new FileInputStream(args[0]),new FileOutputStream(args[1]));
			}
		}else{
			//No arguments given
			init(System.in,System.out);
		}
		//Start transaction manager
		TransactionManager.getInstance().run();
	}
	
	/**
	 * Method init is used to initialize TransactionManager and SiteManager
	 * @param in : InputStream either default i.e. Standard input or from a file
	 * @param out : OutputStream 
	 * @throws Exception : if the initialization of Transaction manager and Site manager fails then an exception is thrown
	 * @author Shashank
	 */
	public static void init(InputStream in, OutputStream out) throws Exception{
		try{
			//Initialize the transaction manager
			TransactionManager.init(in,out);
		}catch(Exception e){
			//If initialization of transaction manager fails then throw an appropriate Exception
			throw new Exception("Initialization of the Transaction manager failed");
		}
		try{
			//Initialize site manager that initializes the sites with appropriate variables and initial values. 
			initSites();
		}catch(Exception e){
			//If initialization of site manager fails then throw an appropriate Exception
			throw new Exception("Initialization of the Site Manager failed");
		}
	}
	
	/**
	 * Method initSites() creates 10 sites and initializes their variables and puts the variables in the data items table.
	 * Even variables are replicated, odd variables are not.
	 * @author Deepti Verma
	 */
	public static void initSites(){
		for(int i=1;i<=10;i++){ //Creating 10 new sites
			Site s=new Site(i); 
			for(int j=1;j<=20;j++){
				//Add even data items to all sites
				if(j%2==0){
					//Even data items are replicated
					DataItem dt=new DataItem();
					Value v=new Value();
					v.timestamp=-1; //Set the initial timestamp
					v.value=10*j; //Initial value
					dt.isReplicated=true; //It is replicated
					dt.availablForRead=true; //Initially all the data items are available for read
					dt.dataIdentifier="x"+j; //Set the dataidentifier for the data item
					dt.valueList.add(v); //Add initial value to the list
					s.dataItems.put(dt.dataIdentifier,dt); //Adding the initial values to the secondary storage
				}else{
					//Add odd data items to one site each 
					if(i==1+(j%10)){ //Odd variables
						DataItem dt=new DataItem();
						Value v=new Value();
						v.timestamp=-1; 
						v.value=10*j;
						dt.dataIdentifier="x"+j;
						dt.isReplicated=false;
						dt.availablForRead=true;
						dt.valueList.add(v);
						s.dataItems.put(dt.dataIdentifier,dt);
					}
				}
			}
			TransactionManager.getInstance().addSite(s); //Add site to the site list so that it is maintained by the transaction manager
		}
	}
}
