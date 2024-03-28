import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Client 
{	
	public static void main(String[] args)
	{
		String hostName = "192.168.0.35"; // Server's IP
		int portNumber = 3849;
		
		int clientID = -1;

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
		
		try 
		{			
			// Create a DatagramSocket for sending and receiving UDP packets
            DatagramSocket socket = new DatagramSocket();

            // Get the IP address of the server (in this case, localhost)
            InetAddress serverAddress = InetAddress.getByName(hostName);

            // Send clientID to the server
            String id = String.valueOf(clientID);
            byte[] sendData = id.getBytes();
            
            // Create a DatagramPacket to send the clientID data to the server
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, portNumber);
            
            // Send the packet to the server
            socket.send(sendPacket);

            // Receive response from server
            // Create a byte array to store received data
            byte[] receiveData = new byte[1024];
            // Create a DatagramPacket to receive a response from the server
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            // Receive the response packet from the server
            socket.receive(receivePacket);
            // Convert the received data to a string
            String response = new String(receivePacket.getData()).trim();
            System.out.println("Response from server: " + response);

            // Close the socket
            socket.close();
        } 
		catch (Exception e) 
		{
            e.printStackTrace();
        }
		
		System.out.println("Finished");
	}
}