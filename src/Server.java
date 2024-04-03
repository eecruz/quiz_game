import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
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
			
			// Create a DatagramSocket to listen for incoming UDP packets on port 9876
			dgSocket = new DatagramSocket(portNumber);
		}
		
		catch (Exception e) 
		{
			System.err.println("ERROR while initializing sockets");
			e.printStackTrace();
		}
		
		// Threads
		RunThread runThread = new RunThread();
		TCPThread tcpThread = new TCPThread(serverSocket);
		UDPThread udpThread = new UDPThread(dgSocket);
		udpThread.start();

		try 
		{
			System.out.println("Server created");
			System.out.println("Waiting for client connections...");

			// Client connections
			int clientID = 1;

			// Start waiting for "start" command
			runThread.start();

			// Accept connections from clients before game starts
			while (!runThread.isRunCommandReceived())
			{
				try 
				{					
					// Safely remove closed sockets
					Iterator<Socket> socketIterator = tcpThread.getClientSockets().iterator();
					while (socketIterator.hasNext()) 
					{
					    Socket socket = socketIterator.next();
					    if (socket.isClosed()) 
					    {
					        System.out.println("Client disconnected... Removing socket...");
					        socketIterator.remove(); // Safe removal
					    }
					}

					// Safely remove killed clients
					Iterator<ClientThread> clientThreadIterator = tcpThread.getClientThreads().iterator();
					while (clientThreadIterator.hasNext()) 
					{
					    ClientThread client = clientThreadIterator.next();
					    if (client.isKilled()) 
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
				catch (SocketTimeoutException e) 
				{
					// Allow the main thread to periodically check for the "start" command
					continue;
				} 
				catch (IOException e) 
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
			Path tempFile = Files.createTempFile("question", ".txt");
	        try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile)) 
	        {
	        	File file = new File("question_format.txt");
				Scanner scanner = new Scanner (new FileInputStream(file));
				
				// Write question to temporary file
				while(scanner.hasNext())
				{
					fileWriter.write(scanner.nextLine());
					fileWriter.newLine();
				}
				
				// Write question file to all clients
				tcpThread.writeFileToAllClients(tempFile.toFile());
	        }
	        
	        // Wait for polling to complete
	        Boolean pollingComplete;
	        while(pollingComplete = !udpThread.isPollingComplete())
	        {
	        	// Do nothing
	        }
	        
	        int first = udpThread.getFirstPoll();
	        
	        System.out.println("POLLING COMPLETE");
	        System.out.println("CLIENT TO ANSWER: " + first);
	        
			
			try 
			{
				// Wait for game to finish
				tcpThread.join();
			} 
			catch (InterruptedException e) 
			{
				System.err.println("Game interrupted");
			}
		} 
		catch (Exception e) 
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
		while (!runCommandReceived) 
		{
			System.out.println("Enter 'start' to begin game:");
			String input = "";

			try 
			{
				input = terminalInput.readLine().trim();
			}
			catch (IOException e) 
			{
				System.err.println("ERROR with input");
				e.printStackTrace();
			}

			if (input.equalsIgnoreCase("start")) 
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
		catch (IOException e) 
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
	
	private boolean gameInProgress;

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
	
	public int getNumClients()
	{
		return clientSockets.size();
	}
	
	public boolean gameInProgress()
	{
		return gameInProgress;
	}
	
	public ArrayList<ClientThread> getClientThreads()
	{
		return clientThreads;
	}
	
	public ArrayList<Socket> getClientSockets()
	{
		return clientSockets;
	}
	
	public void writeStringToAllClients(String str) 
	{
		for(ClientThread client : clientThreads)
		{
			client.writeStringToClient(str);
		}
	}
	
	public void writeFileToAllClients(File file)
	{
		for(ClientThread client : clientThreads)
		{
			client.writeFileToClient(file);
		}
	}

	@Override
	public void run()
	{
		// Set clientID based on current number of clients
		clientID = clientSockets.size() + 1;

		// Indicate game has started
		gameInProgress = true;
		
		while (true)
		{
			try 
			{	
				// Safely remove closed sockets
				Iterator<Socket> socketIterator = clientSockets.iterator();
				while (socketIterator.hasNext()) 
				{
				    Socket socket = socketIterator.next();
				    if (socket.isClosed()) 
				    {
				        System.out.println("Client disconnected... Removing socket...");
				        socketIterator.remove(); // Safe removal
				    }
				}

				// Safely remove killed clients
				Iterator<ClientThread> clientThreadIterator = clientThreads.iterator();
				while (clientThreadIterator.hasNext()) 
				{
				    ClientThread client = clientThreadIterator.next();
				    if (client.isKilled()) 
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
			catch (SocketTimeoutException e) 
			{
				continue;
			} 
			
			catch (IOException e) 
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
		if (isComplete)
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
		if (clientPolls.size() > 0)
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
			while (true) 
			{
				// Create a DatagramPacket to receive incoming UDP packets
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				// Receive data from the client
				socket.receive(receivePacket);

				// Convert the received data to a string (then to an integer)
				String receivedID = new String(receivePacket.getData()).trim();
				int id = Integer.valueOf(receivedID);
				
				// If data represents a buzz from a client
				if (id >= 0)
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
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
}

// Thread to handle each client separately
class ClientThread extends Thread 
{
	private final Socket clientSocket;
	private final int clientID;
	private boolean isKilled;
	private ObjectOutputStream writer;
	private TCPThread tcpThread;

	public ClientThread(Socket clientSocket, int clientID, TCPThread tcpThread) 
	{
		this.clientSocket = clientSocket;
		this.clientID = clientID;
		
		isKilled = false;

		this.tcpThread = tcpThread;
		
		try 
		{
			writer = new ObjectOutputStream(clientSocket.getOutputStream());
		} 
		
		catch (IOException e) 
		{
			System.err.println("ERROR initializing writer for Client " + clientID);
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
	
	public void writeFileToClient(File file) 
	{
		try 
		{
			writer.writeObject(file);
			writer.flush();
		} 
		catch (IOException e) 
		{
			System.err.println("ERROR writing file to client " + clientID);
			e.printStackTrace();
		}
	}
	
	public void writeStringToClient(String str) 
	{
		try 
		{
			writer.writeObject(str);
			writer.flush();
		} 
		catch (IOException e) 
		{
			System.err.println("ERROR writing string to client " + clientID);
			e.printStackTrace();
		}
	}
	
	public void writeIntToClient(int x) 
	{
		try 
		{
			writer.writeInt(x);
			writer.flush();
		} 
		catch (IOException e) 
		{
			System.err.println("ERROR writing integer to client " + clientID);
			e.printStackTrace();
		}
	}

	@Override
	public void run() 
	{
		ObjectInputStream reader = null;

		// Send client ID to the client
		writeIntToClient(clientID);
		
		if(tcpThread.gameInProgress())
			writeStringToClient("wait");

		try
		{
			reader = new ObjectInputStream(clientSocket.getInputStream());

			while (true)
			{
				// End client thread
				if(clientSocket.isClosed())
					break;
				
				try 
				{
					clientSocket.setSoTimeout(1000);
					Object input = reader.readObject();

					// Client requests kill
					if(input instanceof String && ((String)input).equals("kill"))
					{
						clientSocket.close();
						System.out.println("Killing Client " + clientID + "...");
						isKilled = true;
					}
				}
				catch (SocketTimeoutException e) 
				{
					// Prevent blocking while listening for object
					continue;
				}
			} 
		} 
		
		catch (ClassNotFoundException e) 
		{
			System.err.println("ERROR receiving input while closing");
			e.printStackTrace();
		}
		
		catch (IOException e) 
		{
			System.err.println("ERROR closing client");
			e.printStackTrace();
		} 
	} 

}