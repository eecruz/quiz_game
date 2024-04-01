import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server 
{
	public static void main(String[] args) throws IOException 
	{
		int portNumber = 3849;

		ServerSocket serverSocket = new ServerSocket(portNumber);

		// Threads
		RunThread runThread = new RunThread();
		TCPThread tcpThread = new TCPThread(serverSocket);
		Thread udpThread;

		DatagramSocket dgSocket;

		try 
		{
			System.out.println("Server created");
			System.out.println("Waiting for client connections...");

			// Client connections
			int clientID = 1;

			// Start waiting for "start" command
			runThread.start();

			try 
			{
				// Create a DatagramSocket to listen for incoming UDP packets on port 9876
				dgSocket = new DatagramSocket(3849);

				// Create a thread to handle incoming packets
				udpThread = new Thread(new UDPThread(dgSocket));
				udpThread.start(); // Start the receiver thread
			} 
			catch (Exception e) 
			{
				System.err.println("ERROR INITIALZING UDP SOCKET");
				e.printStackTrace();
			}

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
					
					// Set a timeout for accepting new client connections
					serverSocket.setSoTimeout(1000);

					// Accept connection
					Socket clientSocket = serverSocket.accept();
					System.out.println("Connection accepted from Client " + clientID);

					// Add client socket to list
					ClientThread clientThread = new ClientThread(clientSocket, clientID);
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

			try {
				tcpThread.join(); // Wait for the other thread to finish executing
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} 
		catch (Exception e) 
		{
			System.err.println("Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
			System.out.println(e.getMessage());
		}

		// Continue execution on the main thread
		System.out.println("Finished");
		System.exit(1);
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

// Thread for mid-game client connections
class TCPThread extends Thread 
{
	// Clients
	private ArrayList<ClientThread> clientThreads;
	private ArrayList<Socket> clientSockets;
	private int clientID;

	// Server
	private ServerSocket serverSocket;

	// Constructor to initialize the socket
	public TCPThread(ServerSocket serverSocket) 
	{
		// Client list
		clientThreads = new ArrayList<ClientThread>();
		clientSockets = new ArrayList<Socket>();

		this.serverSocket = serverSocket;
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
	
	public ArrayList<ClientThread> getClientThreads()
	{
		return clientThreads;
	}
	
	public ArrayList<Socket> getClientSockets()
	{
		return clientSockets;
	}

	@Override
	public void run()
	{
		// Set clientID based on current number of clients
		clientID = clientSockets.size() + 1;

		while (true)
		{
			try 
			{
//				for (Socket socket : clientSockets)
//				{
//					System.out.println("SOCKET INFO FOR " + (clientSockets.indexOf(socket) + 1));
//					if (socket.isClosed())
//					{
//						System.out.println("Client disconnected... Removing socket...");
//						clientSockets.remove(socket);
//						System.out.println("REMAINING CLIENTS: " + clientSockets.size());
//					}
//				}
//				
//				for (ClientThread client : clientThreads)
//				{
//					System.out.println("THREAD INFO FOR " + client.getClientID());
//					if (client.isKilled())
//					{
//						System.out.println("Removing thread for client " + client.getClientID() + "...");
//						clientThreads.remove(client);
//					}
//				}
				
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
				ClientThread clientThread = new ClientThread(clientSocket, clientID);
				clientThreads.add(clientThread);
				clientSockets.add(clientSocket);
				clientThread.start();

				// Increment clientID
				clientID++;
			} 
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


// Runnable class to handle incoming packets
class UDPThread implements Runnable 
{
	private DatagramSocket socket;
	private ConcurrentLinkedQueue<Integer> clientPolls;

	// Constructor to initialize the socket
	public UDPThread(DatagramSocket socket) 
	{
		this.socket = socket;
		clientPolls = new ConcurrentLinkedQueue<Integer>();
	}

	@Override
	public void run() 
	{
		try 
		{
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
				System.out.println("Received buzz from Client " + id);

				// Add client's buzz to queue
				clientPolls.add(Integer.valueOf(id));
				
				System.out.println("QUEUE: ");
				for(int i = 0; i < clientPolls.size(); i++)
				{
					Object[] array = clientPolls.toArray();
					
					if(i != clientPolls.size() - 1)
						System.out.print(array[i].toString() + ", ");
					
					else
						System.out.println(array[i].toString());
				}
				

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

	public ClientThread(Socket clientSocket, int clientID) 
	{
		this.clientSocket = clientSocket;
		this.clientID = clientID;

		isKilled = false;
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

	@Override
	public void run() 
	{
		ObjectOutputStream writer = null;
		ObjectInputStream reader = null;
		try 
		{
			// Send client ID to the client
			writer = new ObjectOutputStream(clientSocket.getOutputStream());

			writer.writeInt(clientID);
			writer.flush();           

			// Close connection
			//writer.close();
			//clientSocket.close();
		} 
		catch (IOException e) 
		{
			System.err.println("ERROR handling client");
			e.printStackTrace();
		}


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
