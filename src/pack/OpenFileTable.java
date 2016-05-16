package pack;

/**
 * The OpenFileTable class will implement the entry of the Open File Table of the File System.
 * It provides a number of different constructions that suit better different situations you may find 
 * when opening a file: an empty entry (not assigned to any file), an newly created file (it's empty),
 * or a regular file (it's not empty). It also provides a method to free an OFT entry.
 * @author David García Santacruz, ID#: 51062654
 *
 */
public class OpenFileTable {
	
	PackableMemory buffer;
	int currentPosition;
	int fileDescIndex;
	int length;
	
	
	/**
	 * Class constructor with no parameters. Creates an unused OFT entry.
	 */
	public OpenFileTable(){
		buffer = new PackableMemory(LDisk.BLOCK_LENGTH); 
		currentPosition = 0;
		fileDescIndex = -1;		//Empty
		length = -1;			//Empty
	}
	
	
	/**
	 * Class constructor with parameters to create an OFT entry for a file. Used for empty files.
	 * @param index		file descriptor index of the file.
	 * @param len		length of the file.
	 */
	public OpenFileTable(int index, int len){
		buffer = new PackableMemory(LDisk.BLOCK_LENGTH); 
		currentPosition = 0;
		fileDescIndex = index;
		length = len;
	}
	
	
	/**
	 * Class constructor with parameters to create an OFT entry for a file. Used for non-empty files.
	 * @param block		block of the file to be copied into the OTF entry buffer.
	 * @param index		file descriptor index of the file.
	 * @param len		length of the file.
	 */
	public OpenFileTable(PackableMemory block, int index, int len){
		buffer = block; 
		currentPosition = 0;
		fileDescIndex = index;
		length = len;
	}
	
	
	/**
	 * Free an OFT entry, by setting its descriptor index and length to -1.
	 */
	public void free(){
		fileDescIndex = -1;		//Empty
		length = -1;			//Empty
	}
	
	
	/**
	 * Display in the console a visual representation of the OFT entry.
	 * Implemented for debugging purposes.
	 */
	public void print(){
		System.out.print("BUFFER | ");
		for(int i = 0; i<buffer.mem.length; i++){
			System.out.print(buffer.mem[i] + " ");
		}
		System.out.print("|  " + "cur pos: " + currentPosition + "; fd: " + fileDescIndex + "; length: " + length + "\n");
	}
}