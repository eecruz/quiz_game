import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Client 
{	
	public static void main(String[] args)
	{
		String hostName = "10.111.119.204"; // Server's IP
		int portNumber = 5555;
		// String hostName = "10.141.65.116"; // Server's IP
		// int portNumber = 3849;
		
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
		
		try {			
			// Create a DatagramSocket for sending and receiving UDP packets
            DatagramSocket socket = new DatagramSocket();

            // Get the IP address of the server (in this case, localhost)
            InetAddress serverAddress = InetAddress.getByName(hostName);
            int serverPort = 3849; // Port number the server is listening on

            // Sending timestamp to the server
            // Create a timestamp (in this case, current time in milliseconds) to send to the server
            String timestamp = String.valueOf(System.currentTimeMillis());
            byte[] sendData = timestamp.getBytes();
            // Create a DatagramPacket to send the timestamp data to the server
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            // Send the packet to the server
            socket.send(sendPacket);

            // Optional: Receive response from server
            // Create a byte array to store received data
            byte[] receiveData = new byte[1024];
            // Create a DatagramPacket to receive a response from the server
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            // Receive the response packet from the server
            socket.receive(receivePacket);
            // Convert the received data to a string
            String response = new String(receivePacket.getData());
            System.out.println("Response from server: " + response);

            // Close the socket
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
		
		System.out.println("Finished");
	}
}