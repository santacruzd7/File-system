package pack;

/**
 * The LDisk class will implement a logical disk to emulate a physical disk along with its various functions.
 * The functions to interact with the LDisk are read_block and write_block, since we can only access the disk
 * by blocks, never by individual bytes.
 * @author David García Santacruz, ID#: 51062654
 */
public class LDisk {
	
	public static final int NUM_BLOCKS = 64; // Number of logical blocks on LDisk
	public static final int BLOCK_LENGTH = 64; // Block length (in bytes)
	
	private PackableMemory blocks; // LDisk

	/**
	 * Class constructor
	 */
	public LDisk(){
		blocks = new PackableMemory(NUM_BLOCKS*BLOCK_LENGTH);	// LDisk size is NUM_BLOCKS*BLOCK_LENGTH
	}
	
	
	/**
	 * Read block i from LDisk and copy its content to myBlock.
	 * myBlock needs to have the same size as an LDisk block.
	 * @param i			index of the block to be read from the LDisk.				
	 * @param myBlock	block where the content of the block will be copied. 
	 * 					Must the be same size as an LDisk block in order to read into it.	
	 */
	public void read_block(int i, PackableMemory myBlock){
		// Check size of the block equals LDisk block size, otherwise error
		if (myBlock.size != BLOCK_LENGTH){
			return;
		}
		
		// Find the first byte of the block
		int pos = i*BLOCK_LENGTH;
		
		//Copy the block from the ldisk to myBlock
		for (int a = 0; a<BLOCK_LENGTH; a++){
			myBlock.mem[a] = blocks.mem[pos];
			pos++;
		}
	}
	
	
	/**
	 * Write the content of myBlock into block i from LDisk.
	 * myBlock needs to have the same size as an LDisk block.
	 * @param i			index of the block to be written into the LDisk.
	 * @param myBlock	block from which the content will be copied.
	 * 					Must the be same size as an LDisk block in order to read into it.
	 */
	public void write_block(int i, PackableMemory myBlock){
		// Check size of the block equals LDisk block size, otherwise error
		if (myBlock.size != BLOCK_LENGTH){
			return;
		}
		
		// Find the first byte of the block
		int pos = i*BLOCK_LENGTH;
		
		//Copy the block from the ldisk to myBlock
		for (int a = 0; a<BLOCK_LENGTH; a++){
			blocks.mem[pos] = myBlock.mem[a];
			pos++;
		}
	}
	
	
	/**
	 * Displays a visual representation of the contents of the LDisk in the console.
	 * Implemented for debugging purposes.
	 */
	public void print(){
		for(int i = 0; i<15; i++){
			System.out.print("BLOCK " + i + " | ");
			for(int j = 0; j<BLOCK_LENGTH; j++){
				System.out.print(blocks.mem[i*BLOCK_LENGTH+j] + " ");
			}
			System.out.print("| \n");
		}
	}
}