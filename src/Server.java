import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
	public static void main(String[] args) throws IOException 
	{
		int portNumber = 3849;
		RunThread runThread = new RunThread();
		Thread udpThread;
		DatagramSocket dgSocket;

		try (ServerSocket serverSocket = new ServerSocket(portNumber)) 
		{
			System.out.println("Server created");
			System.out.println("Waiting for client connections...");

			// Client connections
			int clientID = 1;
			ArrayList<Socket> clientSockets = new ArrayList<>();

			// Start waiting for "start" command
			runThread.start();
			
			try 
			{
				// Create a DatagramSocket to listen for incoming UDP packets on port 9876
				dgSocket = new DatagramSocket(3849);

				// Create a thread to handle incoming packets
				udpThread = new Thread(new UDPThread(dgSocket));
				udpThread.start(); // Start the receiver thread
			} catch (Exception e) {
				e.printStackTrace();
			}

			while (!runThread.isRunCommandReceived())  //eseentially while true
			{
				try 
				{
					// Set a timeout for accepting new client connections
					serverSocket.setSoTimeout(1000);

					// Accept connection
					Socket clientSocket = serverSocket.accept();
					System.out.println("Connection accepted from Client " + clientID);

					// Provide ID to client
					ObjectOutputStream writer = new ObjectOutputStream(clientSocket.getOutputStream());
					writer.writeInt(clientID);
					writer.close();

					// Add client socket to list
					clientSockets.add(clientSocket);
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


class RunThread extends Thread 
{
	private volatile boolean runCommandReceived = false;
	private final Object lock = new Object();

	@Override
	public void run()
	{
		// Waits continuously for the "start" command
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

//Runnable class to handle incoming packets
class UDPThread implements Runnable {
	private DatagramSocket socket;

	// Constructor to initialize the socket
	public UDPThread(DatagramSocket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			byte[] receiveData = new byte[1024];

			// Infinite loop to continuously listen for incoming packets
			while (true) {
				System.out.println("ATTEMPTING TO RECEIVE PACKET");
				// Create a DatagramPacket to receive incoming UDP packets
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				System.out.println("PACKET CREATED");
				
				// Receive data from the client
				socket.receive(receivePacket);
				System.out.println("PACKET RECEIVED");
				
				// Convert the received data to a string (in this case, assuming it's a timestamp)
				String receivedTimestamp = new String(receivePacket.getData());
				System.out.println("Received from client: " + receivedTimestamp);

				// Process the received data here if needed

				// Get the client's address and port from the received packet
				InetAddress clientAddress = receivePacket.getAddress();
				int clientPort = receivePacket.getPort();

				// Respond to the client (optional)
				String response = "Timestamp received by server";
				byte[] sendData = response.getBytes();
				// Create a DatagramPacket to send a response back to the client
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
				// Send the response packet back to the client
				socket.send(sendPacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}