import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server 
{
	public static void main(String[] args)
	{
		int portNumber = 3849;
		
		// Sockets
		ServerSocket serverSocket = null;
		DatagramSocket dgSocket = null;
		
		try 
		{
			// Create a socket for TCP communication
			serverSocket = new ServerSocket(portNumber);
			
			// Create a DatagramSocket to listen for incoming UDP packets
			dgSocket = new DatagramSocket(portNumber);
		}
		
		catch(Exception e) 
		{
			System.err.println("ERROR while initializing sockets");
			e.printStackTrace();
		}
		
		// Threads
		RunThread runThread = new RunThread();
		TCPThread tcpThread = new TCPThread(serverSocket);
		UDPThread udpThread = new UDPThread(dgSocket);
		udpThread.start();

		// Create answer key for questions (index 0 is empty so answers match with question number)
		String[] answerKey = new String[21];

		// Obtain answers from text file
		try 
		{
			File keyFile = new File("questions/answer_key.txt");
			Scanner scanner = new Scanner (new FileInputStream(keyFile));
			
			int index = 1;

			// Read values from file
			while(scanner.hasNext() && index < answerKey.length)
			{
				answerKey[index] = scanner.nextLine().trim();
				index++;
			}
			
			scanner.close();
		} 
		
		catch (FileNotFoundException e) 
		{
			System.err.println("ERROR initalizing answer key");
			e.printStackTrace();
		}
		

		try 
		{
			System.out.println("Server created");
			System.out.println("Waiting for client connections...");

			// Client connections
			int clientID = 1;

			// Start waiting for "start" command
			runThread.start();

			// Accept connections from clients before game starts
			while(!runThread.isRunCommandReceived())
			{
				try 
				{					
					// Safely remove closed sockets
					Iterator<Socket> socketIterator = tcpThread.getClientSockets().iterator();
					while(socketIterator.hasNext()) 
					{
					    Socket socket = socketIterator.next();
					    if(socket.isClosed()) 
					    {
					        System.out.println("Client disconnected... Removing socket...");
					        socketIterator.remove(); // Safe removal
					    }
					}

					// Safely remove killed clients
					Iterator<ClientThread> clientThreadIterator = tcpThread.getClientThreads().iterator();
					while(clientThreadIterator.hasNext()) 
					{
					    ClientThread client = clientThreadIterator.next();
					    if(client.isKilled()) 
					    {
					        System.out.println("Removing thread for client " + client.getClientID() + "...");
					        clientThreadIterator.remove(); // Safe removal
					        System.out.println("Remaining Clients: " + tcpThread.getNumClients());
					    }
					}
					
					// Roll back disconnected clientIDs before game starts
					clientID = tcpThread.getNumClients() + 1;
					
					// Set a timeout for accepting new client connections
					serverSocket.setSoTimeout(1000);

					// Accept connection
					Socket clientSocket = serverSocket.accept();
					System.out.println("Connection accepted from Client " + clientID);

					// Add client socket to list
					ClientThread clientThread = new ClientThread(clientSocket, clientID, tcpThread);
					tcpThread.addClientThread(clientThread);
					tcpThread.addClientSocket(clientSocket);
					clientThread.start();

					// Increment clientID
					clientID++;
				} 
				catch(SocketTimeoutException e) 
				{
					// Allow the main thread to periodically check for the "start" command
					continue;
				} 
				catch(IOException e) 
				{
					System.err.println("ERROR handling client acceptances");
					e.printStackTrace();
					break;
				}
			}

			// Continue listening for TCP connections after game starts
			tcpThread.start();
			System.out.println("TCPThread started");

			// Tell clients to start game
			tcpThread.writeStringToAllClients("start");
			
			//TODO Loop through multiple questions to send to clients
			Path tempFile = Files.createTempFile("q1", ".txt");
	        try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile)) 
	        {
	        	File file = new File("questions/question1.txt");
				Scanner fileReader = new Scanner (new FileInputStream(file));
				
				// Write question to temporary file
				while(fileReader.hasNext())
				{
					fileWriter.write(fileReader.nextLine());
					fileWriter.newLine();
				}
				
				// Write question file to all clients
				tcpThread.writeFileToAllClients(tempFile.toFile());
				
				// Close temp file streams
				fileWriter.close();
				fileReader.close();
	        }
	        
	        // Wait for polling to complete
	        Boolean pollingComplete;
	        while(pollingComplete = !udpThread.isPollingComplete())
	        {
	        	// Do nothing
	        	Thread.sleep(0);
	        }
	        
	        // Get ID for client who won the poll
	        int ackClientID = udpThread.getFirstPoll();
	        
	        System.out.println("POLLING COMPLETE");
	        System.out.println("CLIENT TO ANSWER: " + ackClientID);
	        
	        // Alert clients whether they won the poll
	        tcpThread.ackClients(ackClientID);
	        System.out.println("CLIENTS ACKED");
	        
	        // Wait for answer from client
	        Boolean answerReceived;
	        while(answerReceived = !tcpThread.isAnswerReceived())
	        {
	        	// Do nothing
	        	Thread.sleep(0);
	        }
	        
	        // Get status of client's answer (e.g. correct, incorrect)
	        String answerStatus = tcpThread.isClientAnswerCorrect(ackClientID, answerKey[1]);
			
	        // Inform clients of answer status
	        tcpThread.informClientsOfStatus(answerStatus, ackClientID);
	        
			try 
			{
				// Wait for game to finish
				tcpThread.join();
			} 
			catch(InterruptedException e) 
			{
				System.err.println("Game interrupted");
			}
		} 
		catch(Exception e) 
		{
			System.err.println("Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
			System.out.println(e.getMessage());
		}

		// Continue execution on the main thread
		System.out.println("Finished");
		System.exit(0);
	}
}




// Thread to wait for start of game
class RunThread extends Thread 
{
	private volatile boolean runCommandReceived = false;
	private final Object lock = new Object();

	@Override
	public void run()
	{
		// Wait continuously for the "start" command
		BufferedReader terminalInput = new BufferedReader(new InputStreamReader(System.in));
		while(!runCommandReceived) 
		{
			System.out.println("Enter 'start' to begin game:");
			String input = "";

			try 
			{
				input = terminalInput.readLine().trim();
			}
			catch(IOException e) 
			{
				System.err.println("ERROR with input");
				e.printStackTrace();
			}

			if(input.equalsIgnoreCase("start")) 
			{
				synchronized (lock) 
				{
					// Notify main thread
					runCommandReceived = true;
					lock.notify();
				}
			} 

			else
				System.out.println("Invalid command. Please enter 'start' to begin.");
		}

		try 
		{
			terminalInput.close();
		} 
		catch(IOException e) 
		{
			System.err.println("ERROR closing input stream");
			e.printStackTrace();
		}
	}

	public boolean isRunCommandReceived() 
	{
		return runCommandReceived;
	}
}

// Thread for mid-game client connections and terminating clients
class TCPThread extends Thread 
{
	// Clients
	private ArrayList<ClientThread> clientThreads;
	private ArrayList<Socket> clientSockets;
	private int clientID;
	
	// Indicate whether game has started
	private boolean gameInProgress;
	
	// Indicate whether answer has been received by client
	private Boolean answerReceived;

	// Server
	private ServerSocket serverSocket;

	// Constructor to initialize the socket
	public TCPThread(ServerSocket serverSocket) 
	{
		// Client list
		clientThreads = new ArrayList<ClientThread>();
		clientSockets = new ArrayList<Socket>();

		this.serverSocket = serverSocket;
		
		gameInProgress = false;
		answerReceived = false;
	}

	// Add a client to thread list
	public void addClientThread(ClientThread clientThread)
	{
		clientThreads.add(clientThread);
	}

	// Add a client to socket list
	public void addClientSocket(Socket clientSocket)
	{
		clientSockets.add(clientSocket);
	}
	
	// Get number of currently active clients
	public int getNumClients()
	{
		return clientSockets.size();
	}
	
	// Indicate whether the game has started
	public boolean gameInProgress()
	{
		return gameInProgress;
	}
	
	// Get a list of current client's threads
	public ArrayList<ClientThread> getClientThreads()
	{
		return clientThreads;
	}
	
	// Get a list of current client's sockets
	public ArrayList<Socket> getClientSockets()
	{
		return clientSockets;
	}
	
	// Write string to all current clients
	public void writeStringToAllClients(String str) 
	{
		for(ClientThread client : clientThreads)
		{
			client.writeStringToClient(str);
		}
	}
	
	// Write file to all current clients
	public void writeFileToAllClients(File file)
	{
		for(ClientThread client : clientThreads)
		{
			client.writeFileToClient(file);
		}
	}
	
	// Alert clients whether they won the poll, or if nobody polled
	public void ackClients(int ackClientID)
	{
		// At least one client polled
		if(ackClientID != -1)
		{
			for (ClientThread client : clientThreads)
			{
				// Alert client with the fastest poll that they can answer
				if(client.getClientID() == ackClientID)
					client.writeStringToClient("ack");

				// Alert other clients that they were late in polling
				else
					client.writeStringToClient("negative-ack");
			}
		}
		
		// No clients polled
		else
		{
			System.out.println("NO POLLS");
			writeStringToAllClients("no-poll");
		}	
	}
	
	// Obtain and check answer from client who won polling, returns whether they answered correctly
	public String isClientAnswerCorrect(int clientID, String correctAnswer)
	{
		String answer = null;
		
		for (ClientThread client : clientThreads)
		{
			// Obtain answer from client with the fastest poll
			if(client.getClientID() == clientID)
				answer = client.getClientAnswer();
		}
		
		// Client answered correctly
		if(answer.equals(correctAnswer))
			return "correct";
		
		// Client did not answer
		else if(answer.equals("no answer"))
			return "penalty";
		
		// Client answered incorrectly
		else
			return "incorrect";
	}
	
	// Tell each client whether the question was answered correctly
	public void informClientsOfStatus(String status, int ackClientID)
	{
		for (ClientThread client : clientThreads)
		{
			// Inform answering client whether they answered correctly
			if(client.getClientID() == ackClientID)
				client.writeStringToClient(status);
			
			// Inform other clients whether the question was answered correctly and who answered it
			else
				client.writeStringToClient("alt_" + status + ackClientID);
		}
	}
	
	// Set the value for whether the client's answer was received
	public void setAnswerReceived(Boolean isAnswerReceived) 
	{
		answerReceived = isAnswerReceived;
	}
	
	// Indicate whether answer has been received by the client who won polling
	public Boolean isAnswerReceived()
	{
		return answerReceived;
	}

	@Override
	public void run()
	{
		// Set clientID based on current number of clients
		clientID = clientSockets.size() + 1;

		// Indicate game has started
		gameInProgress = true;
		
		while(true)
		{
			try 
			{	
				// Safely remove closed sockets
				Iterator<Socket> socketIterator = clientSockets.iterator();
				while(socketIterator.hasNext()) 
				{
				    Socket socket = socketIterator.next();
				    if(socket.isClosed()) 
				    {
				        System.out.println("Client disconnected... Removing socket...");
				        socketIterator.remove(); // Safe removal
				    }
				}

				// Safely remove killed clients
				Iterator<ClientThread> clientThreadIterator = clientThreads.iterator();
				while(clientThreadIterator.hasNext()) 
				{
				    ClientThread client = clientThreadIterator.next();
				    if(client.isKilled()) 
				    {
				        System.out.println("Removing thread for Client " + client.getClientID() + "...");
				        clientThreadIterator.remove(); // Safe removal
				        System.out.println("Remaining Clients: " + getNumClients());
				    }
				}

				// Set a timeout for accepting new client connections
				serverSocket.setSoTimeout(1000);

				// Accept connection
				Socket clientSocket = serverSocket.accept();
				System.out.println("Connection accepted from Client " + clientID);

				// Add client socket to list
				ClientThread clientThread = new ClientThread(clientSocket, clientID, this);
				clientThreads.add(clientThread);
				clientSockets.add(clientSocket);
				clientThread.start();

				// Increment clientID
				clientID++;
			}
			
			// Prevent blocking if client requests kill
			catch(SocketTimeoutException e) 
			{
				continue;
			} 
			
			catch(IOException e) 
			{
				System.err.println("ERROR handling client acceptances");
				e.printStackTrace();
				break;
			}
		}
	}
}

// Thread to handle incoming UDP packets
class UDPThread extends Thread 
{
	private DatagramSocket socket;
	private ConcurrentLinkedQueue<Integer> clientPolls;

	// Constructor to initialize the socket
	public UDPThread(DatagramSocket socket)
	{
		this.socket = socket;
		clientPolls = new ConcurrentLinkedQueue<Integer>();
	}
	
	// Indicate whether polling has completed
	public boolean isPollingComplete()
	{
		Boolean isComplete = clientPolls.contains(-1);
		
		// Remove all -1s from queue
		if(isComplete)
		{
			System.out.print("INITAL ");
			printQueue();
			
			ArrayList<Integer> temp = new ArrayList<>();
			temp.add(-1);
			
			clientPolls.removeAll(temp);
			
			System.out.print("FINAL ");
			printQueue();
		}
		
		return isComplete;
	}
	
	// Print contents of queue
	public void printQueue()
	{
		System.out.println("QUEUE: ");
		for(int i = 0; i < clientPolls.size(); i++)
		{
			Object[] array = clientPolls.toArray();
			
			if(i != clientPolls.size() - 1 && clientPolls.size() > 1)
				System.out.print(array[i].toString() + ", ");
			
			else
				System.out.println(array[i].toString());
		}
	}
	
	// Return first client that polled and clear queue
	public int getFirstPoll()
	{	
		// -1 signals to server that no clients polled
		int first = -1;
		
		// If at least 1 client polls
		if(clientPolls.size() > 0)
			first = clientPolls.poll();
		
		clientPolls.clear();
		return first;
	}

	@Override
	public void run()
	{
		try 
		{
			// Byte array to specify length of incoming packets
			byte[] receiveData = new byte[1024];

			// Infinite loop to continuously listen for incoming packets
			while(true) 
			{
				// Create a DatagramPacket to receive incoming UDP packets
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				// Receive data from the client
				socket.receive(receivePacket);

				// Convert the received data to a string (then to an integer)
				String receivedID = new String(receivePacket.getData()).trim();
				int id = Integer.valueOf(receivedID);
				
				// If data represents a buzz from a client
				if(id >= 0)
					System.out.println("Received buzz from Client " + id);
				
				// Add data to queue
				clientPolls.add(Integer.valueOf(id));
				

//				// Get the client's address and port from the received packet
//				InetAddress clientAddress = receivePacket.getAddress();
//				int clientPort = receivePacket.getPort();
//
//				// Respond to the client
//				String response = "Buzz received by server";
//				byte[] sendData = response.getBytes();
//				// Create a DatagramPacket to send a response back to the client
//				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
//				// Send the response packet back to the client
//				socket.send(sendPacket);
			}
		} 
		catch(Exception e) 
		{
			e.printStackTrace();
		}
	}
}

// Thread to handle each client separately
class ClientThread extends Thread 
{
	// TCP
	private final Socket clientSocket;
	private TCPThread tcpThread;

	// Client info
	private final int clientID;
	private boolean isKilled;
	private String userAnswer;
	
	// Communication
	private ObjectOutputStream writer;
	private ObjectInputStream reader;

	public ClientThread(Socket clientSocket, int clientID, TCPThread tcpThread) 
	{
		this.clientSocket = clientSocket;
		this.clientID = clientID;
		
		// Initial info for client
		isKilled = false;
		userAnswer = null;

		this.tcpThread = tcpThread;
		
		try 
		{
			writer = new ObjectOutputStream(clientSocket.getOutputStream());
			reader = new ObjectInputStream(clientSocket.getInputStream());
		} 
		
		catch(IOException e) 
		{
			System.err.println("ERROR initializing writer/reader for Client " + clientID);
			e.printStackTrace();
		}
	}

	// Return clientID
	public int getClientID() 
	{
		return clientID;
	}

	// Return whether this client has been killed
	public boolean isKilled() 
	{
		return isKilled;
	}
	
	// Write a file to client
	public void writeFileToClient(File file) 
	{
		try 
		{
			writer.writeObject(file);
			writer.flush();
		} 
		catch(IOException e) 
		{
			System.err.println("ERROR writing file to client " + clientID);
			e.printStackTrace();
		}
	}
	
	// Write a string to client
	public void writeStringToClient(String str) 
	{
		try 
		{
			writer.writeObject(str);
			writer.flush();
		} 
		catch(IOException e) 
		{
			System.err.println("ERROR writing string to client " + clientID);
			e.printStackTrace();
		}
	}
	
	// Write an integer to client
	public void writeIntToClient(int x) 
	{
		try 
		{
			writer.writeInt(x);
			writer.flush();
		} 
		catch(IOException e) 
		{
			System.err.println("ERROR writing integer to client " + clientID);
			e.printStackTrace();
		}
	}
	
	// Get answer if this client won the poll
	public String getClientAnswer()
	{
		String answer = userAnswer;
		
		// Reset client's answer
		userAnswer = null;
		
		// Return client's answer
		return answer;
	}

	@Override
	public void run() 
	{
		// Send client ID to the client
		writeIntToClient(clientID);
		
		// Signal client to wait for next question if game has already started
		if(tcpThread.gameInProgress())
			writeStringToClient("wait");

		while(true)
		{
			// End client thread
			if(clientSocket.isClosed())
				break;

			// Listen for incoming TCP messages from client
			try
			{
				clientSocket.setSoTimeout(1000);
				Object input = reader.readObject();

				// Server receives incoming TCP message from client
				if(input instanceof String)
				{
					// Client requests kill
					if(((String)input).equals("kill"))
					{
						clientSocket.close();
						System.out.println("Killing Client " + clientID + "...");
						isKilled = true;
					}
					
					// Client submitted answer
					else 
					{
						// Obtain user's answer
						userAnswer = (String)input;
						
						// Alert server that answer has been received
						tcpThread.setAnswerReceived(true);
					}
				}
			}

			catch(SocketTimeoutException e) 
			{
				// Prevent blocking while listening for object
				continue;
			}

			catch(ClassNotFoundException e) 
			{
				System.err.println("ERROR receiving input while closing");
				e.printStackTrace();
			}

			catch(IOException e) 
			{
				System.err.println("ERROR closing client");
				e.printStackTrace();
			} 
		}
	} 

}