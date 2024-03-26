import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    public static void main(String[] args) throws IOException 
    {
        int portNumber = 5555;
        RunThread runThread = new RunThread();

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) 
        {
            System.out.println("Server created");
            System.out.println("Waiting for client connections...");

            // Client connections
            int clientID = 1;
            ArrayList<Socket> clientSockets = new ArrayList<>();

            // Start waiting for "start" command
            runThread.start();

            while (!runThread.isRunCommandReceived()) 
            {
                try 
                {
                    // Set a timeout for accepting new client connections
                    serverSocket.setSoTimeout(1000); // Timeout of 1 second

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
