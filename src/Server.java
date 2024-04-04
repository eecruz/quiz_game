import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
		UDPThread udpThread = new UDPThread(dgSocket);
		udpThread.start();
		
		TCPThread tcpThread = new TCPThread(serverSocket, udpThread);
		RunThread runThread = new RunThread();

		

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
			tcpThread.setCurrentClientID(clientID);
			tcpThread.start();
			System.out.println("Game started");

			// Tell clients to start game
			tcpThread.writeStringToAllClients("start");
			
			// Begin game with question 1
			for(int questionNum = 1; questionNum < answerKey.length; questionNum++)
			{
				// Clear queue for this question
				udpThread.clearPolls();
				
				// Create temporary file to send to client
				Path tempFile = Files.createTempFile("q" + questionNum, ".txt");
				try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile)) 
				{
					File file = new File("questions/question" + questionNum + ".txt");
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
				
				Thread.sleep(2000);

				// Wait for polling to complete
				Boolean pollingIncomplete;
				while(pollingIncomplete = !udpThread.isPollingComplete())
				{
					// Do nothing
					Thread.sleep(250);
				}

				// Get ID for client who won the poll
				int ackClientID = udpThread.getFirstPoll();

				// Alert clients whether they won the poll
				tcpThread.ackClients(ackClientID);

				// At least one client polled (skip waiting for answer if no clients poll)
				if(ackClientID != -1)
				{
					// Wait for answer from client
					Boolean answerReceived;
					while(answerReceived = !tcpThread.isAnswerReceived())
					{
						// Do nothing
						Thread.sleep(0);
					}
					
					// Get status of client's answer (e.g. correct, incorrect)
					String answerStatus = tcpThread.isClientAnswerCorrect(ackClientID, answerKey[questionNum]);

					// Inform clients of answer status
					tcpThread.informClientsOfStatus(answerStatus, ackClientID);
				}
				
				// Wait a few seconds before issuing next question (or ending game)
				Thread.sleep(4000);
				
				// If there are more questions, ready clients for next question
				if(questionNum < answerKey.length - 1)
				{
					tcpThread.writeStringToAllClients("next");
					tcpThread.setAnswerReceived(false);
					Thread.sleep(100);
				}
				
				// If this was the last question, signal the clients that the game is over
				else
				{
					// Obtain clientID of the winning client
					ArrayList<Integer> winningClientIDs = tcpThread.getWinners();
					
					// Alert clients that game has ended, and alert winning client that they won
					tcpThread.writeEndToAllClients(winningClientIDs);
					
					// Print winner(s)
					if(winningClientIDs.size() > 1)
					{
						// Multiple winners
						System.out.print("Winners: ");
						
						for(int i = 0; i < winningClientIDs.size() - 1; i++)
						{
							System.out.print("Client " + winningClientIDs.get(i) + ", ");
						}
						
						System.out.println("Client " + winningClientIDs.get(winningClientIDs.size()-1));
					}
					
					// Single winner
					else
						System.out.println("WINNER: Client " + winningClientIDs.get(0));
				}
			}
		} 
		catch(Exception e) 
		{
			System.err.println("Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
			e.printStackTrace();
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
	
	// UDPThread
	UDPThread udpThread;

	// Constructor to initialize the socket
	public TCPThread(ServerSocket serverSocket, UDPThread udpThread) 
	{
		// Client list
		clientThreads = new ArrayList<ClientThread>();
		clientSockets = new ArrayList<Socket>();

		this.serverSocket = serverSocket;
		
		this.udpThread = udpThread;
		
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
	
	// Set value of current clientID to be assigned
	public void setCurrentClientID(int currentClientID)
	{
		clientID = currentClientID;
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
			{
				client.writeStringToClient(status);
				client.updateClientScore(status);
			}
			
			// Inform other clients whether the question was answered correctly and who answered it
			else
				client.writeStringToClient("alt_" + status + ackClientID);
		}
	}
	
	// Get client ID of winning client
	public ArrayList<Integer> getWinners()
	{
		// Create list for winners
		ArrayList<Integer> winners = new ArrayList<Integer>();
		
		// Track high score across the clients
		int winningScore = clientThreads.get(0).getClientScore();
		
		for (ClientThread client : clientThreads)
		{
			int score = client.getClientScore();
			
			// Update highScore if this client has a better score
			if(score > winningScore)
			{
				winningScore = score;
			}
		}
		
		// Add clients with highScore to winners list
		for (ClientThread client : clientThreads)
		{
			if(client.getClientScore() == winningScore)
				winners.add(client.getClientID());
		}
		
		// Return clientID of the winning client
		return winners;
	}
	
	// Alert clients that game is over, and whether they won
	public void writeEndToAllClients(ArrayList<Integer> winners)
	{
		for (ClientThread client : clientThreads)
		{
			// Client has the highest score for the game
			if(winners.contains(client.getClientID()))
				client.writeStringToClient("win");
			
			// Client did not win
			else
				client.writeStringToClient("end");				
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
				        udpThread.removeClientPolls(client.getClientID());
				    	System.out.println("Removing thread for Client " + client.getClientID() + "...");
				        clientThreadIterator.remove(); // Safe removal
				        System.out.println("Remaining Clients: " + getNumClients());
				    }
				}
				
				if(getNumClients() == 0)
				{
					System.out.println("All clients have disconnected. Ending game...");
					System.exit(0);
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
			ArrayList<Integer> temp = new ArrayList<>();
			temp.add(-1);
			
			clientPolls.removeAll(temp);
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
	
	// Remove all polls from queue
	public void clearPolls()
	{
		clientPolls.clear();
	}
	
	// Remove all polls associated with a specific client
	public void removeClientPolls(int clientID)
	{
		// Remove all -1s from queue	
			ArrayList<Integer> temp = new ArrayList<>();
			temp.add(clientID);

			clientPolls.removeAll(temp);
	}
	
	// Return first client that polled and clear queue
	public int getFirstPoll()
	{	
		// -1 signals to server that no clients polled
		int first = -1;
		
		// If at least 1 client polls
		if(clientPolls.size() > 0)
			first = clientPolls.poll();
				
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
	private int score;
	
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
		score = 0;

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
		
		catch(SocketException e1)
		{
			System.err.println("ERROR writing file to client " + clientID + "... Socket is closed");
		}
		
		catch(IOException e2) 
		{
			System.err.println("ERROR writing file to client " + clientID);
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
		
		catch(SocketException e1)
		{
			System.err.println("ERROR writing string to client " + clientID + "... Socket is closed");
		}
		
		catch(IOException e2) 
		{
			System.err.println("ERROR writing string to client " + clientID);
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
		
		catch(SocketException e1)
		{
			System.err.println("ERROR writing integer to client " + clientID + "... Socket is closed");
		}
		
		catch(IOException e2) 
		{
			System.err.println("ERROR writing integer to client " + clientID);
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
	
	public void updateClientScore(String status)
	{
		// Client answered correctly
		if(status.equals("correct"))
			score += 10;
		
		// Client answered incorrectly
		else if(status.equals("incorrect"))
			score -= 10;
		
		// Client didn't answer after polling
		else if(status.equals("penalty"))
			score -= 20;
	}
	
	// Return current score of this client
	public int getClientScore()
	{
		return score;
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