import java.io.ObjectInputStream;
import java.net.Socket;

public class Client 
{	
	public static void main(String[] args)
	{
		String hostName = "192.168.0.35"; // Server's IP
		int portNumber = 5555;
		
		int clientID;

		try 
		{
			// Establish connection
			Socket socket = new Socket(hostName, portNumber);
			System.out.println("Connection accepted");
			
			// Receive clientID from server
			ObjectInputStream reader = new ObjectInputStream(socket.getInputStream());
			clientID = reader.readInt();
			System.out.println("ClientID: " + clientID);
			
			// Close connection
			reader.close();
			socket.close();
		}
		catch (Exception e) 
		{
			System.err.println("Couldn't get I/O for the connection");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Finished");
	}
}