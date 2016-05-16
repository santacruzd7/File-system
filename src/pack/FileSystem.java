package pack;

import java.io.*;
import java.util.ArrayList;

/**
 * The FileSystem class will implement the file system and its various functions to interact with the contents of the disk.
 * These functions are: create a file, destroy a file, open a file, close a file, read from a file, write to a file,
 * seek to a position in the file, get the directory, initialize disk (or restore it) and save the disk.
 * Additionally, a set of internal methods have been developed to assist the functionality of the aforementioned funtions.
 * @author David García Santacruz, ID#: 51062654
 */
public class FileSystem {

	public static final int MAX_NUM_OPEN_FILES = 4;
	public static final int MAX_FILE_NAME = 4;
	public static final int DISK_MAP = 3;
	public static final int SLOT_SIZE = 8; // Bytes

	public static final int NUM_DESCRIPTORS = 24;
	public static final int FD_LENGTH = 16; // Bytes
	public static final int NUM_FD_BLOCKS = NUM_DESCRIPTORS * FD_LENGTH / LDisk.BLOCK_LENGTH; // 6
	public static final int NUM_FD_PER_BLOCK = LDisk.BLOCK_LENGTH / FD_LENGTH; // 4

	private LDisk myDisk;
	private OpenFileTable[] OFT;
	private int[] MASK;

	
	/**
	 * Class constructor.
	 * Creates a FileSystem containing an empty LDisk. 
	 * Initializes the OFT, the bitmap, the file descriptors and the directory.
	 */
	public FileSystem() {
		// Initialize the LDisk, which is empty
		myDisk = new LDisk();

		// Initialize the OFT and each of its entries (as empty)
		OFT = new OpenFileTable[MAX_NUM_OPEN_FILES];
		for (int i = 0; i < OFT.length; i++) {
			OFT[i] = new OpenFileTable();
		}

		// Initialize the mask to work with the bitmap
		initializeMask();

		// Initialize bitmap
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);	// Block to read/write from/to the LDisk
			
		// Read the bitmap and mark blocks 0...6 as used by setting their bit to 1 with the corresponding mask
		myDisk.read_block(0, temp_block); // Read the BM
		for(int i = 0; i<=6; i++){
			int temp_BM = temp_block.unpack(0);
			temp_BM = temp_BM | MASK[i];
			temp_block.pack(temp_BM, 0);
		}
		myDisk.write_block(0, temp_block); // Write the changes back to the disk
		
		// Initialize file descriptors, all empty (length and block numbers equal to -1)
		// Block containing only empty FD
		PackableMemory temp_desc = new PackableMemory(LDisk.BLOCK_LENGTH);	
		for(int i = 0; i<LDisk.BLOCK_LENGTH; i = i+4){
			temp_desc.pack(-1, i);
		}
		// Copy the block with empty FD to the blocks 1...6, which contain FD
		for(int i = 1; i<=NUM_FD_BLOCKS; i++){
			myDisk.write_block(i, temp_desc); 
		}
		
		// Initialize directory
		// Set length of FD 0 to 0
		myDisk.read_block(1, temp_block);
		temp_block.pack(0, 0);
		myDisk.write_block(1, temp_block);
		OFT[0] = new OpenFileTable(0, 0);
		
		// Create a generic free slot (length = -1)
		PackableMemory free_slot = new PackableMemory(SLOT_SIZE);
		free_slot.pack(-1, 4);
		
		// Write the 24 free slots to the directory
		lseek(0, 0);
		for(int i = 0; i<NUM_DESCRIPTORS; i++){
			write(0, free_slot.mem, SLOT_SIZE);
		}
	}
	
	
	/**
	 * Creates a new file in the LDisk, given its name, as long as there are available file descriptors / slots.
	 * @param file_name		name of the file to be created. Must be at most four chars and unique.
	 * @return				boolean status: 'true' for success; 'false' for error.
	 */
	public boolean create(String file_name) {
		// Check file name length (error if larger than 4)
		if (file_name.length()>MAX_FILE_NAME){
			return false;
		}
		
		// Check file name uniqueness (error if not unique)
		if (fileNameExists(file_name)){
			return false;
		}
		
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);	// Block to read/write from/to the LDisk

		// 1. Find a free file descriptor
		int free_desc_index = -1;
		
		boolean found = false;
		// Iterate through all the FD blocks
		for (int i = 1; i <= NUM_FD_BLOCKS && !found; i++) { 
			myDisk.read_block(i, temp_block);
			// Iterate through all the FD in the block
			for (int j = 0; j < LDisk.BLOCK_LENGTH && !found; j = j + FD_LENGTH) { 
				int length = temp_block.unpack(j);
				if (length < 0) {
					found = true;
					temp_block.pack(0, j); // For a free FD -> update length to 0
					myDisk.write_block(i, temp_block);
					free_desc_index = (i - 1) * NUM_FD_PER_BLOCK + j / FD_LENGTH;
				}
			}
		}
		
		// If there are no free descriptors, there is an error
		if(free_desc_index == -1){
			return false;
		}
		
		// 2. Find a free directory entry
		// Go to the beginning of the directory
		lseek(0, 0);
		
		PackableMemory temp_slot = new PackableMemory(SLOT_SIZE); 	// Block to work with slots
		// Iterate over all the slots
		for (int i = 0; i < OFT[0].length; i = i + SLOT_SIZE) { 
			lseek(0, i);
			read(0, temp_slot.mem, SLOT_SIZE);
			// Find a free slot
			if (temp_slot.unpack(4) < 0) { 
				// 3. Fill both entries
				// Update name
				for (int j = 0; j < file_name.length(); j++) {
					temp_slot.mem[j] = (byte) file_name.toCharArray()[j];
				}
				// Update descriptor index
				temp_slot.pack(free_desc_index, 4);
				
				lseek(0, i); // Beginning of slot to write
				write(0, temp_slot.mem, SLOT_SIZE); // Overwrite slot with new info.
				
				return true;
			}
		}
		
		// If there are not a free slots, there is an error
		return false;
	}
	
	
	/**
	 * Destroys a file from the LDisk, given its name. The file must exist and not be opened.
	 * @param file_name		name of the file to be destroyed. Must be at most four chars.
	 * @return				boolean status: 'true' for success; 'false' for error.
	 */
	public boolean destroy(String file_name){
		// Check file name length (error if larger than 4)
		if (file_name.length()>MAX_FILE_NAME){
			return false;
		}
		
		// 1. Search the directory to find file descriptor
		int file_desc = findFileDesc(file_name);
		// If there file does not exist, there is an error
		if(file_desc == -1){
			return false;
		}
		
		// Check the file is not open, if it is error
		for (int i = 0; i < OFT.length; i++) {
			if (OFT[i].fileDescIndex == file_desc) {
				return false;
			}
		}
		
		// 4. Free file descriptor
		freeFileDesc(file_name);
		
		// 2. Remove directory entry
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH); // Block to read/write from/to the LDisk
		
		// Getting block where descriptor is
		int desc_block = file_desc/NUM_FD_PER_BLOCK + 1;
		myDisk.read_block(desc_block, temp_block);
		
		// Storing the block numbers of used blocks of the file in an ArrayList (as long as file is not empty)
		int length = temp_block.unpack(file_desc%NUM_FD_PER_BLOCK*FD_LENGTH);
		ArrayList<Integer> blocks = new ArrayList<Integer>();
		for(int i = 4; i<FD_LENGTH && length > 0; i=i+4){ 
			int temp_block_num = temp_block.unpack(file_desc%NUM_FD_PER_BLOCK*FD_LENGTH+i);
			if(temp_block_num>0){
				blocks.add(temp_block_num);
			}
		}
		
		temp_block.pack(-1, file_desc%NUM_FD_PER_BLOCK*FD_LENGTH); // Set the length to -1 to mark descriptor as free
		myDisk.write_block(desc_block, temp_block);
		
		//3. Update bitmap
		// Read the bitmap and mark used blocks as free by setting their bit to 0 with the corresponding mask
		myDisk.read_block(0, temp_block); //Read the BM
		for(int i = 0; i<blocks.size(); i++){
			int temp_BM = temp_block.unpack(blocks.get(i)/32*32);
			temp_BM = temp_BM & ~MASK[blocks.get(i)%32];
			temp_block.pack(temp_BM, blocks.get(i)/32*32);
		}
		myDisk.write_block(0, temp_block);
		
		//5. Return status
		return true;
	}

	
	/**
	 * Opens a file from the LDisk, given its name, as long as there are available entries in the OFT. 
	 * The file must exist and not be opened.
	 * @param file_name		name of the file to be opened. Must be at most four chars.
	 * @return				OFT index; -1 for error.
	 */
	public int open(String file_name) {
		// Check file name length (error if larger than 4)
		if (file_name.length()>MAX_FILE_NAME){
			return -1;
		}
		
		// 1. Search directory to find index of file descriptor
		int file_desc = findFileDesc(file_name);
		// If there file does not exist, there is an error
		if(file_desc == -1){
			return -1;
		}
		
		// Check the file has not already been opened. If so, there is an error
		for (int i = 0; i < OFT.length; i++) {
			if (OFT[i].fileDescIndex == file_desc) {
				return -1;
			}
		}
		
		// 2. Allocate a free OFT entry reusing deleted entries
		for (int i = 0; i < OFT.length; i++) {
			if (OFT[i].length == -1) {
				// Read the descriptor block
				int desc_block = file_desc / NUM_FD_PER_BLOCK + 1;
				PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
				myDisk.read_block(desc_block, temp_block);
				// Find the file length
				int length = temp_block.unpack(file_desc % NUM_FD_PER_BLOCK * FD_LENGTH);
				// Allocate the OFT entry depending on whether the file is empty or not
				if(length > 0){	// File is not empty -> There is a first block
					int first_block = temp_block.unpack(file_desc % NUM_FD_PER_BLOCK * FD_LENGTH + 4);
					myDisk.read_block(first_block, temp_block);
					OFT[i] = new OpenFileTable(temp_block, file_desc, length);
				} else { // File is empty -> There is not a first block (the buffer is initialized with an empty block)
					OFT[i] = new OpenFileTable(file_desc, length);
				}
				return i;
			}
		}

		// If there are not free entries in the OFT, there is an error
		return -1;
	}
	
	
	/**
	 * Closes a file from the LDisk, given its OFT index. The file must be open.
	 * @param index		index in the OFT of the file to be closed. It must be within the OFT boundaries.
	 * @return			boolean status: 'true' for success; 'false' for error.
	 */
	public boolean close(int index){
		// Check index is within OFT boundaries, if not error
		if(!(index >= 0 && index <OFT.length)){
			return false;
		}
		
		// Check the OFT entry does contain an open file, if not error
		if(OFT[index].fileDescIndex == -1){
			return false;
		}
		
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
		
		// Read the descriptor block
		int desc_block = OFT[index].fileDescIndex/NUM_FD_PER_BLOCK+1;
		myDisk.read_block(desc_block, temp_block);
		
		// 2. Update file length in descriptor
		temp_block.pack(OFT[index].length, OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH);
		myDisk.write_block(desc_block, temp_block);
		
		// Only if the file is not empty (it was been written), // 1. Write buffer to disk
		if(OFT[index].length > 0){
			writeBufferToDisk(index);
		}
		
		// 3. Free OFT entry
		OFT[index].free();
		
		// 4. Return status
		return true;
	}

	
	/**
	 * Reads a given number of bytes from an open file, given its OFT index, into a memory area. The file must be open.
	 * @param index		index in the OFT of the file to be read. It must be within the OFT boundaries.
	 * @param mem_area	memory area where the bytes read will be copied. Its size must be at least 'count'.
	 * @param count		number of bytes to read from the file.
	 * 					If the count tries to read beyond the end of the file, it reads only up to the end of the file.
	 * @return			number of bytes read, -1 for error.
	 */
	public int read(int index, byte [] mem_area, int count){
		// Check index is within OFT boundaries, if not error
		if(!(index >= 0 && index <OFT.length)){
			return -1;
		}
		
		// Check the OFT entry does contain an open file, if not error
		if(OFT[index].fileDescIndex == -1){
			return -1;
		}
		
		// Check the memory area is large enough to store the amount of bytes to be read
		if(mem_area.length < count){
			return -1;
		}
		
		// 1. Compute position in the R/W buffer
		int bufferPos = OFT[index].currentPosition%LDisk.BLOCK_LENGTH;
		
		int bytesRead = 0;
		
		// 2. Copy from buffer to memory (until desired count or end of file is reached)
		for (int i = 0; i<count && OFT[index].currentPosition < OFT[index].length; i++){
			mem_area[i] = OFT[index].buffer.mem[bufferPos];
			bytesRead++;
			bufferPos++;
			
			// End of buffer reached
			if(bufferPos >= LDisk.BLOCK_LENGTH){
				// Write the buffer to disk
				writeBufferToDisk(index);
				
				// Read the next block
				readNextBlockIntoBuffer(index);
				
				bufferPos = 0;
			}
			OFT[index].currentPosition++;	
		}
		
		// Return the number of bytes read
		return bytesRead;
	}

	
	/**
	 * Writes a given number of bytes into an open file, given its OFT index, from a memory area. The file must be open.
	 * @param index		index in the OFT of the file to be written. It must be within the OFT boundaries.
	 * @param mem_area	memory area where the bytes to be written reside. Its size must be at least 'count'.
	 * @param count		number of bytes to write into the file. 
	 * 					If the count tries to write beyond the maximum file size, it reads only up to the maximum file size.
	 * @return			number of bytes written; -1 for error.	
	 */
	public int write(int index, byte [] mem_area, int count){
		// Check index is within OFT boundaries, if not error
		if(!(index >= 0 && index <OFT.length)){
			return -1;
		}
		
		// Check the OFT entry does contain an open file, if not error
		if(OFT[index].fileDescIndex == -1){
			return -1;
		}
		
		// Check the memory area is large enough to write such amount of bytes
		if(mem_area.length < count){
			return -1;
		}
		
		// 1. Compute position in the R/W buffer
		int bufferPos = OFT[index].currentPosition%LDisk.BLOCK_LENGTH;
		
		int bytesWritten = 0;
		
		// 2. Copy from memory to buffer (until desired count or end of file is reached)
		for (int i = 0; i<count && OFT[index].currentPosition < LDisk.BLOCK_LENGTH*DISK_MAP; i++){
			OFT[index].buffer.mem[bufferPos] = mem_area[i];
			bytesWritten++;
			
			bufferPos++;
			
			// End of buffer reached
			if(bufferPos >= LDisk.BLOCK_LENGTH){
				// Write buffer to disk
				writeBufferToDisk(index);
				
				// Read the next block checking whether a next block does exist
				readNextBlockIntoBuffer(index);

				bufferPos = 0;
			}
			OFT[index].currentPosition++;
		}

		// Update file length
		int newLength = OFT[index].currentPosition - OFT[index].length;
		if(newLength > 0){
			OFT[index].length += newLength;
		}
		
		// Return status
		return bytesWritten;
	}
	
	
	/**
	 * Places the cursor of an open file, given its OFT, to a new given position. The file must be open.
	 * @param index		index in the OFT of the file to be seek. It must be within the OFT boundaries.
	 * @param pos		new position to place the cursor. It must be within the maximum file size. 
	 * 					If it goes beyond the file length and write is performed after, 
	 * 					the space between the length and the new position is filled with the 0 char.
	 * @return			boolean status: 'true' for success; 'false' for error.
	 */
	public boolean lseek(int index, int pos){
		// Check index is within OFT boundaries, if not error
		if(!(index >= 0 && index <OFT.length)){
			return false;
		}
		
		// Check the OFT entry does contain an open file, if not error
		if(OFT[index].fileDescIndex == -1){
			return false;
		}
		
		// Check position is within file boundaries, if not error
		if(!(pos >= 0 && pos <= LDisk.BLOCK_LENGTH*DISK_MAP)){
			return false;
		} 
		
		// Find the current block within the file and the new block
		int current_block = OFT[index].currentPosition / LDisk.BLOCK_LENGTH + 1; // which of three blocks currently are
		// In case we reached the last position
		if(current_block > DISK_MAP){
			current_block = DISK_MAP;
		}
		
		int new_block = pos / LDisk.BLOCK_LENGTH + 1;
		
		// 1. If the new position is not within the current block
		if (current_block != new_block){
			// Write buffer to disk
			writeBufferToDisk(index);

			// Read new block
			PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
			myDisk.read_block(OFT[index].fileDescIndex/NUM_FD_PER_BLOCK + 1, temp_block);
			//myDisk.read_block(temp_block.unpack(OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH + new_block*4), OFT[index].buffer);	
			
			int new_block_num = temp_block.unpack(OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH + new_block*4);
			if(new_block_num == -1){
			OFT[index].buffer = new PackableMemory(LDisk.BLOCK_LENGTH);
			} else {
				myDisk.read_block(new_block_num, OFT[index].buffer);
			}
		}
		
		// 2. Set the current position to the new position
		OFT[index].currentPosition = pos;
		
		// 3. Return status
		return true;
	}
	
	
	/**
	 * Prints the names of all the files in the directory.
	 * @return		String representing the directory, that is, the names of all the existing files separated by a whitespace.
	 */
	
	public String directory(){
		String directory = "";
		
		// Go to the beginning of the directory
		lseek(0, 0);
		
		PackableMemory temp_slot = new PackableMemory(SLOT_SIZE);
		
		// Read the directory entries and append the name of each existing file to the string 
		for(int i = 0; i<OFT[0].length; i = i+SLOT_SIZE){
			read(0, temp_slot.mem, SLOT_SIZE);
			
			// Append the name of the file only if the slot entry is not empty
			int temp_fd = temp_slot.unpack(4);
			if(temp_fd >= 0){
				char [] nameChar = new char [4];
				for(int j = 0; j<4; j++){
					nameChar[j] = (char) temp_slot.mem[j];
				}
				String name = new String(nameChar);
				
				if(!name.trim().isEmpty()){
					directory = directory.concat(name.trim().concat(" "));
				}
			}
		}
		
		return directory;
	}
	
	
	/**
	 * Initializes the disk, setting up the bitmap and opening the directory.
	 */
	public void init() {
		// Initialize the LDisk, which is empty
		myDisk = new LDisk();

		// Initialize the OFT and each of its entries (as empty)
		OFT = new OpenFileTable[MAX_NUM_OPEN_FILES];
		for (int i = 0; i < OFT.length; i++) {
			OFT[i] = new OpenFileTable();
		}

		// Initialize the mask to work with the bitmap
		initializeMask();

		// Initialize bitmap
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH); // Block to read/write from/to the LDisk

		// Read the bitmap and mark blocks 0...6 as used by setting their bit to 1 with the corresponding mask
		myDisk.read_block(0, temp_block); // Read the BM
		for (int i = 0; i <= 6; i++) {
			int temp_BM = temp_block.unpack(0);
			temp_BM = temp_BM | MASK[i];
			temp_block.pack(temp_BM, 0);
		}
		myDisk.write_block(0, temp_block); // Write the changes back to the disk

		// Initialize file descriptors, all empty (length and block numbers equal to -1)
		// Block containing only empty FD
		PackableMemory temp_desc = new PackableMemory(LDisk.BLOCK_LENGTH);
		for (int i = 0; i < LDisk.BLOCK_LENGTH; i = i + 4) {
			temp_desc.pack(-1, i);
		}
		// Copy the block with empty FD to the blocks 1...6, which contain FD
		for (int i = 1; i <= NUM_FD_BLOCKS; i++) {
			myDisk.write_block(i, temp_desc);
		}

		// Initialize directory
		// Set length of FD 0 to 0
		myDisk.read_block(1, temp_block);
		temp_block.pack(0, 0);
		myDisk.write_block(1, temp_block);
		OFT[0] = new OpenFileTable(0, 0);

		// Create a generic free slot (length = -1)
		PackableMemory free_slot = new PackableMemory(SLOT_SIZE);
		free_slot.pack(-1, 4);

		// Write the 24 free slots to the directory
		lseek(0, 0);
		for (int i = 0; i < NUM_DESCRIPTORS; i++) {
			write(0, free_slot.mem, SLOT_SIZE);
		}
	}
	
	
	/**
	 * Restores the disk, given a file with a disk state saved. 
	 * If the file does exist the disk is restored, otherwise it is initialized as an empty disk.
	 * @param fileName		name of the file which contains the disk state.
	 * @return				status: '0' for success (disk restored); '1' for success (disk initialized); '-1' for error.
	 */
	public int init(String fileName){
		File file = new File(fileName);
		
		try {
			if(!file.exists()) {
				init();
                return 1;
			}
			
			// Initialize the LDisk, which is empty
			myDisk = new LDisk();

			// Initialize the OFT and each of its entries (as empty)
			OFT = new OpenFileTable[MAX_NUM_OPEN_FILES];
			for (int i = 0; i < OFT.length; i++) {
				OFT[i] = new OpenFileTable();
			}

			// Initialize the mask to work with the bitmap
			initializeMask();
			
            // Use this for reading the data
			PackableMemory block = new PackableMemory(LDisk.BLOCK_LENGTH);

            FileInputStream inputStream = new FileInputStream(file);

            // read fills buffer with data and returns
            // the number of bytes read (which of course
            // may be less than the buffer size, but
            // it will never be more).
            int total = 0;
            int nRead = 0;
            while((nRead = inputStream.read(block.mem)) != -1) {
            	myDisk.write_block(total / LDisk.BLOCK_LENGTH, block);
                // Convert to String so we can display it.
                // Of course you wouldn't want to do this with
                // a 'real' binary file.
                //System.out.println(new String(buffer));
                total += nRead;
            }           
            
            // Always close files
            inputStream.close();       
        }
        catch(FileNotFoundException ex) {
        	return -1;
        }
        catch(IOException ex) {
        	return -1;
        }
		
		// Open directory
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
		
		// Read directory's length and first block number from the directory's FD
		myDisk.read_block(1, temp_block);
		int dir_length = temp_block.unpack(0);
		int first_dir_block = temp_block.unpack(4);
		
		// Read directory's first block and open an entry in the OFT for it
		myDisk.read_block(first_dir_block, temp_block);
		OFT[0] = new OpenFileTable(temp_block, 0, dir_length);
		
		return 0;
	}
	
	
	/**
	 * Saves the state of the disk into the given file, creating a new file if it doesn't exist.
	 * @param fileName		name of the file which will contain the disk state.
	 * @return				boolean status: 'true' for success; 'false' for error.
	 */
	public boolean save(String fileName){
		// Close all the files before saving so that all changes in the buffer are recorded
		for(int i = 0; i<OFT.length; i++){
			close(i);
		}
		
		File file = new File(fileName);
 
        try {
            // Put some bytes in a buffer so we can
            // write them. Usually this would be
            // image data or something. Or it might
            // be unicode text.
            //String bytes = "Hello theren";
            //byte[] buffer = bytes.getBytes();
        	if(!file.exists()) {
                file.createNewFile();
            }
        	
            FileOutputStream outputStream = new FileOutputStream(file);
            
            PackableMemory block = new PackableMemory(LDisk.BLOCK_LENGTH);
            for(int i = 0; i<LDisk.NUM_BLOCKS; i++){
            	myDisk.read_block(i, block);
            	outputStream.write(block.mem);
            }

            // Always close files.
            outputStream.close();
            return true;
        }
        catch(IOException ex) {
            return false;
        }
	}
	
	
	/**
	 * Initializes the values of the mask that will be used to manipulate individual bits of the bitmap.
	 */
	private void initializeMask(){
		MASK = new int[32];
		MASK[31] = 1;
		for(int i = 30; i>=0; i--){
			MASK[i] = MASK[i+1] << 1;
		}
	}
	
	
	/**
	 * Reads the directory to find if the given file name is unique or not, that is, if it is already used by another file.
	 * @param file_name		name of the file to be checked for uniqueness.
	 * @return				'true' if it does already exist in the directory; 'false' if not.
	 */
	private boolean fileNameExists(String file_name){
		boolean exists = false;
		lseek(0, 0);
		PackableMemory temp_slot = new PackableMemory(SLOT_SIZE); 	// Block to work with slots
		
		// Traverse the directory looking for the file name
		for(int i = 0; i<OFT[0].length && !exists; i = i+SLOT_SIZE){
			read(0, temp_slot.mem, SLOT_SIZE);
			// Obtain the name from each slot
			char [] nameChar = new char [4];
			for(int j = 0; j<4; j++){
				nameChar[j] = (char) temp_slot.mem[j];
			}
			String name = new String(nameChar);
			// Check if it matches
			if(name.trim().equals(file_name)){
				exists = true;
			}
		}
		return exists;
	}
	
	
	/**
	 * Finds the file descriptor for a file, given its name
	 * @param file_name		name of the file for which the file descriptor will be seek. Must be at most four chars.
	 * @return				the file descriptor index; -1 if not found
	 */
	private int findFileDesc(String file_name){
		int file_desc = -1;
		
		// Go to the beginning of the directory
		lseek(0, 0);
		PackableMemory temp_slot = new PackableMemory(SLOT_SIZE);
		for (int i = 0; i < OFT[0].length; i = i + SLOT_SIZE) { // Iterate over all the slots
			read(0, temp_slot.mem, SLOT_SIZE); // Read a slot
			
			// Read the name from the slot
			char [] nameChar = new char [4];
			for(int j = 0; j<4; j++){
				nameChar[j] = (char) temp_slot.mem[j];
			}
			
			String name = new String(nameChar);
			
			// Find the name
			if (name.trim().equals(file_name) ){
				file_desc = temp_slot.unpack(4); // Record file descriptor
				break;
			}
		}	
		
		return file_desc;
	}
	
	
	/**
	 * Finds and frees the file descriptor for a file, given its name.
	 * @param file_name		name of the file for which the file descriptor will be freed. Must be at most four chars.
	 */
	private void freeFileDesc(String file_name){
		// Go to the beginning of the directory
		lseek(0, 0);
		PackableMemory temp_slot = new PackableMemory(SLOT_SIZE);
		for (int i = 0; i < OFT[0].length; i = i + SLOT_SIZE) { // Iterate over all the slots
			read(0, temp_slot.mem, SLOT_SIZE); // Read a slot
			
			// Read the name from the slot
			char [] nameChar = new char [4];
			for(int j = 0; j<4; j++){
				nameChar[j] = (char) temp_slot.mem[j];
			}
			
			String name = new String(nameChar);
			
			// Find the name
			if (name.trim().equals(file_name) ){
				// 4. Free file descriptor
				temp_slot.pack(-1, 4);	
				lseek(0, i); // Beginning of slot to write
				write(0, temp_slot.mem, SLOT_SIZE); // Overwrite slot with new info.
				
				break;
			}
		}	
	}
	
	
	/**
	 * Writes the buffer of an open file, given its OFT index, into the LDisk.
	 * @param index		index of the file in the OFT.
	 */
	private void writeBufferToDisk(int index){
		// Write buffer to disk
		int current_block = OFT[index].currentPosition/LDisk.BLOCK_LENGTH + 1;
		// In case we reached the last position
		if(current_block >= DISK_MAP){
			current_block = DISK_MAP;
		}
		
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
		myDisk.read_block(OFT[index].fileDescIndex/NUM_FD_PER_BLOCK + 1, temp_block);
		
		int current_block_num = temp_block.unpack(OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH + current_block*4);
		
		// Allocate new block if block does not exist
		if(current_block_num == -1){
			// Allocate a new block through the bitmap
			myDisk.read_block(0, temp_block);
			boolean found = false;
			for(int j = 0; j<2 && !found; j++){
				for(int k = 0; k<32 && !found; k++){
					int temp_BM = temp_block.unpack(j*4);
					int test = temp_BM & MASK[k];
					if(test == 0){
						// Set the bit of this block to 1, as it will be used
						temp_BM = temp_BM | MASK[k];
						temp_block.pack(temp_BM, j*4);
						myDisk.write_block(0, temp_block);
						// Update the current block number to the new block found
						current_block_num = j*32+k;
						found = true;
					}
				}
			}
			// Update file descriptor with new block number
			myDisk.read_block(OFT[index].fileDescIndex/NUM_FD_PER_BLOCK + 1, temp_block);
			temp_block.pack(current_block_num, OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH + current_block*4);
			myDisk.write_block(OFT[index].fileDescIndex/NUM_FD_PER_BLOCK + 1, temp_block);
		}
		// Write the buffer to disk
		myDisk.write_block(current_block_num, OFT[index].buffer);
	}
	
	
	/**
	 * Reads the next block of an open file, given its OFT index, and copies it into the OFT buffer.
	 * @param index		index of the file in the OFT.
	 */
	private void readNextBlockIntoBuffer(int index){
		// Read the next block checking whether a next block does exist
		// Find the current block number (relative to the buffer) and read the block containing the file descriptor of that file
		int current_block = OFT[index].currentPosition/LDisk.BLOCK_LENGTH + 1;
		PackableMemory temp_block = new PackableMemory(LDisk.BLOCK_LENGTH);
		myDisk.read_block(OFT[index].fileDescIndex/NUM_FD_PER_BLOCK + 1, temp_block);
		
		// Only if there is a next block, we proceed to read it
		if(current_block < DISK_MAP){
			// Find the next block number and read it to the buffer if it exists or put an empty block in the buffer otherwise
			int next_block_num = temp_block.unpack(OFT[index].fileDescIndex%NUM_FD_PER_BLOCK*FD_LENGTH + current_block*4 + 4);
			if(next_block_num == -1){
				OFT[index].buffer = new PackableMemory(LDisk.BLOCK_LENGTH);
			} else {
				myDisk.read_block(next_block_num, OFT[index].buffer);
			}
		}
	}
}