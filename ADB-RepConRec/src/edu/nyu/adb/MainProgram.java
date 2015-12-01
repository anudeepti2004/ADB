package edu.nyu.adb;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainProgram {

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
	
	public static void init(InputStream in, OutputStream out) throws Exception{
		try{
			TransactionManager.init(in,out);
		}catch(Exception e){
			throw new Exception("Initialization of the Transaction manager failed");
		}
		try{
			SiteManager.init();
		}catch(Exception e){
			throw new Exception("Initialization of the Site Manager failed");
		}
	}
}
