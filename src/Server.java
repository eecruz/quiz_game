import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
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
					// Set a timeout for accepting new client connections
					serverSocket.setSoTimeout(1000);

					// Accept connection
					Socket clientSocket = serverSocket.accept();
					System.out.println("Connection accepted from Client " + clientID);

					// Add client socket to list
					Thread clientThread = new Thread(new ClientThread(clientSocket, clientID));
					tcpThread.addClientThread(clientThread);
					tcpThread.addClientSocket(clientSocket);
					clientThread.start();
					
					// Increment clientID
					clientID++;
				} 
				catch (java.net.SocketTimeoutException e) 
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
	private ArrayList<Thread> clientThreads;
	private ArrayList<Socket> clientSockets;
	private int clientID;

	// Server
	private ServerSocket serverSocket;

	// Constructor to initialize the socket
	public TCPThread(ServerSocket serverSocket) 
	{
		// Client list
        clientThreads = new ArrayList<>();
        clientSockets = new ArrayList<>();
        
        clientID = clientSockets.size();
		
        this.serverSocket = serverSocket;
	}
	
	// Add a client to thread list
	public void addClientThread(Thread clientThread)
	{
		clientThreads.add(clientThread);
	}
	
	// Add a client to socket list
	public void addClientSocket(Socket clientSocket)
	{
		clientSockets.add(clientSocket);
	}

	@Override
	public void run()
	{
		while (true) 
		{
			try 
			{
				// Set a timeout for accepting new client connections
				serverSocket.setSoTimeout(1000);

				// Accept connection
				Socket clientSocket = serverSocket.accept();
				clientID = clientSockets.size() + 1;
				System.out.println("Connection accepted from Client " + clientID);

				// Add client socket to list
				Thread clientThread = new Thread(new ClientThread(clientSocket, clientID));
				clientThreads.add(clientThread);
				clientSockets.add(clientSocket);
				clientThread.start();
				
				// Increment clientID
				//clientID++;
			} 
			catch (java.net.SocketTimeoutException e) 
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

	// Constructor to initialize the socket
	public UDPThread(DatagramSocket socket) 
	{
		this.socket = socket;
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
				
				// Convert the received data to a string (in this case, assuming it's a timestamp)
				String receivedID = new String(receivePacket.getData()).trim();
				int id = Integer.valueOf(receivedID);
				System.out.println("Received buzz from client " + id);

				// (Process the received data here)

				// Get the client's address and port from the received packet
				InetAddress clientAddress = receivePacket.getAddress();
				int clientPort = receivePacket.getPort();

				// Respond to the client (optional)
				String response = "Buzz received by server";
				byte[] sendData = response.getBytes();
				// Create a DatagramPacket to send a response back to the client
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
				// Send the response packet back to the client
				socket.send(sendPacket);
			}
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
}

// Thread to handle each client separately
class ClientThread implements Runnable 
{
    private final Socket clientSocket;
    private final int clientID;
    
    public ClientThread(Socket clientSocket, int clientID) 
    {
        this.clientSocket = clientSocket;
        this.clientID = clientID;      
    }

    @Override
    public void run() 
    {
        try 
        {
            // Send client ID to the client
            ObjectOutputStream writer = new ObjectOutputStream(clientSocket.getOutputStream());
            writer.writeInt(clientID);
            writer.flush(); // Flush the stream to ensure data is sent            

            // Close connection
            writer.close();
            clientSocket.close();
        } 
        catch (IOException e) 
        {
            System.err.println("ERROR handling client");
            e.printStackTrace();
        }
    }
}