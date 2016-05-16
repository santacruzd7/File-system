package pack;

import java.io.*;

/**
 * The Driver class will implement the interface required for the user to interact with the file system.
 * The interaction takes place by reading an input file, which contains commands, and writing an output file,
 * which will store the results of the different commands.
 * @author David García Santacruz, ID#: 51062654
 */
public class Driver {
	
	FileSystem fs;
	File inputFile;		// File where commands are to be read
	File outputFile;	// File where output is to be written
	
	/**
	 * Class constructor.
	 * Initializes the input and output file paths.
	 */
	public Driver(){
		fs = new FileSystem();
		inputFile = new File("E:/input.txt");
		outputFile = new File("E:/54062651.txt");
    	
		// Check existance of input and output files
    	try {
    		if(!inputFile.exists()) {
    			System.out.println("ERROR: input file doesn't exist");
    		}
    		
    		outputFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Read the input file commands and executes them individually.
	 */
	public void run(){
        
		// This will reference one line - one command of the input file - at a time
        String line = null;

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader = new FileReader(inputFile);

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            
            // Read all the lines - commands - of the input file and execute the command
            while((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
            	executeCommand(line);
            }    

            // Always close files.
            bufferedReader.close();            
        }
        catch(FileNotFoundException ex) {
            System.out.println(
                "Unable to open file '" + inputFile + "'");                
        }
        catch(IOException ex) {
            System.out.println(
                "Error reading file '" + inputFile + "'");                   
            // Or we could just do this: 
            // ex.printStackTrace();
        }
	}
	
	/**
	 * Executes the given command, writing the output to the output file.
	 * @param input		String containing the command to be executed.
	 */
	private void executeCommand(String input){
		
        try {
            // Assume default encoding.
            FileWriter fileWriter = new FileWriter(outputFile, true);

            // Always wrap FileWriter in BufferedWriter.
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            
            // For each blank line in the input file, generate a blank line in the output file (for visual separation).
            if (input.trim().equals("")){
    			bufferedWriter.newLine();
    			
    			// Always close files.
                bufferedWriter.close();
    			return;
    		}
               		
    		// Parse the input String into an array containing each word
    		String [] command = input.split("\\s+");
    		for(String s : command){
    			s = s.replaceAll("[^\\w]", "");
    		}
    		
    		// Check the number of parameters (number of words in the command), if too much error
    		int num_params = command.length;
    		if(!(num_params >= 1 && num_params<=4)){
    			bufferedWriter.write("error");
    			bufferedWriter.newLine();
    			
    			// Always close files.
                bufferedWriter.close();
    			return;
    		}
    		
    		// Determine which file system function to call depending on the command and its paramenters
    		int status;
    		if(num_params == 2 && command[0].equals("cr")){
    			if(fs.create(command[1])){
    				bufferedWriter.write(command[1] + " created");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 2 && command[0].equals("de")){
    			if(fs.destroy(command[1])){
    				bufferedWriter.write(command[1] + " destroyed");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 2 && command[0].equals("op")){
    			status = fs.open(command[1]);
    			if(status != -1){
    				bufferedWriter.write(command[1] + " opened " + status);
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 2 && command[0].equals("cl")){
    			if(fs.close(Integer.parseInt(command[1]))){
    				bufferedWriter.write(command[1] + " closed");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 3 && command[0].equals("rd")){
    			byte [] memory = new byte[Integer.parseInt(command[2])];
    			status = fs.read(Integer.parseInt(command[1]), memory, Integer.parseInt(command[2]));
    			if(status != -1){
    				for(int i = 0; i<status; i++){
    					bufferedWriter.write(memory[i]);
    				}
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 4 && command[0].equals("wr")){
    			byte [] memory = new byte[Integer.parseInt(command[3])];
    			for(int i = 0; i<memory.length; i++){
    				memory[i] = (byte) command[2].charAt(0);
    			}
    			status = fs.write(Integer.parseInt(command[1]), memory, Integer.parseInt(command[3]));
    			if(status != -1){
    				bufferedWriter.write(status + " bytes written");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 3 && command[0].equals("sk")){
    			if(fs.lseek(Integer.parseInt(command[1]), Integer.parseInt(command[2]))){
    				bufferedWriter.write("position is " + command[2]);
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 1 && command[0].equals("dr")){
    			String dir = fs.directory();
    			bufferedWriter.write(dir);
    		} else if (num_params == 1 && command[0].equals("in")) {
    			fs.init();
    			bufferedWriter.write("disk initialized");
    		} else if (num_params == 2 && command[0].equals("in")){
    			status = fs.init(command[1]);
    			if(status == 0){
    				bufferedWriter.write("disk restored");
    			} else if (status == 1){
    				bufferedWriter.write("disk initialized");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else if (num_params == 2 && command[0].equals("sv")){
    			if(fs.save(command[1])){
    				bufferedWriter.write("disk saved");
    			} else {
    				bufferedWriter.write("error");
    			}
    		} else {
    			bufferedWriter.write("error");
    		}
    		
    		bufferedWriter.newLine();
    		
            // Always close files.
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                "Error writing to file '" + outputFile + "'");
            // Or we could just do this:
            // ex.printStackTrace();
        }
	}
}