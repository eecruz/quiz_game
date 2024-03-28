import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.TimerTask;
import java.util.Timer;
import javax.swing.*;

public class ClientWindow implements ActionListener
{
	// Server info
	private String hostIP;
	private InetAddress serverAddress;
	int portNumber;
	
	// Sockets
	private Socket tcpSocket;
	private DatagramSocket udpSocket;
	
	// Buttons
	private JButton poll;
	private JButton submit;
	private JRadioButton options[];
	private ButtonGroup optionGroup;
	
	// Labels
	private JLabel question;
	private JLabel timer;
	private JLabel score;
	
	// Other
	private TimerTask clock;	
	private JFrame window;
	private int clientID;
	
	private static SecureRandom random = new SecureRandom();
	
	// write setters and getters as you need
	
	public ClientWindow()
	{
		// Initial user prompt
		JOptionPane.showMessageDialog(window, "This is a trivia game", 
				"Trivia Game", JOptionPane.PLAIN_MESSAGE);
		
		hostIP = null;
		clientID = -1;
		
		// Prevent user from continuing without providing valid host IP address
		while (true) 
		{
			// Capture input IP from user
			hostIP = JOptionPane.showInputDialog(window, "Please enter the IP address of the host machine (server):", 
					"Trivia Game", JOptionPane.PLAIN_MESSAGE);
			
			// Trim spaces from input
			if (hostIP != null)
				hostIP = hostIP.trim();
			
			// User clicked the "Cancel" button, close the entire application
			else    
			    System.exit(0);
			
			// Valid IP address provided, break out of the loop
            if (isValidIPAddress(hostIP))
            {
            	try 
            	{
					serverAddress = InetAddress.getByName(hostIP);
				} 
            	catch (UnknownHostException e) 
            	{
					System.err.println("Can't determine host IP address");
            		e.printStackTrace();
				}
                break;
            }
            
			// Invalid IP address provided, prompt the user again
            else
                JOptionPane.showMessageDialog(window, "Missing or invalid IP address format. Please try again.",
                		"Invalid IP Address", JOptionPane.ERROR_MESSAGE);
		}
		System.out.println("Valid IP address entered: " + hostIP);

		// Prevent user from continuing without providing valid port number
		while (true) 
		{
			// Capture input port number from user
			String port = JOptionPane.showInputDialog(window, 
					"Please enter the port number used by the host machine (server):", "Trivia Game", JOptionPane.PLAIN_MESSAGE);

			// Trim spaces from input
			if (port != null)
				port = port.trim();

			// User clicked the "Cancel" button, close the entire application
			else    
				System.exit(0);

			// Valid port number provided, break out of the loop
			if (isValidPortNumber(port))
			{
				portNumber = Integer.valueOf(port);
				break;
			}
				
			// Invalid port number provided, prompt the user again
			else
				JOptionPane.showMessageDialog(window, "Missing or invalid port number. Please enter a number between 1024-65535.",
						"Invalid Port Number", JOptionPane.ERROR_MESSAGE);
		}
		System.out.println("Valid port number entered: " + portNumber);

		// Game window
		window = new JFrame("Trivia");
		question = new JLabel("Q1. This is a sample question"); // represents the question
		window.add(question);
		question.setBounds(10, 5, 350, 100);;
		
		options = new JRadioButton[4];
		optionGroup = new ButtonGroup();
		for(int index=0; index<options.length; index++)
		{
			options[index] = new JRadioButton("Option " + (index+1));  // represents an option
			// if a radio button is clicked, the event would be thrown to this class to handle
			options[index].addActionListener(this);
			options[index].setBounds(10, 110+(index*20), 350, 20);
			window.add(options[index]);
			optionGroup.add(options[index]);
		}

		timer = new JLabel("TIMER");  // represents the countdown shown on the window
		timer.setBounds(250, 250, 100, 20);
		clock = new TimerCode(30);  // represents clocked task that should run after X seconds
		Timer t = new Timer();  // event generator
		t.schedule(clock, 0, 1000); // clock is called every second
		window.add(timer);
		
		
		score = new JLabel("SCORE"); // represents the score
		score.setBounds(50, 250, 100, 20);
		window.add(score);

		poll = new JButton("Poll");  // button that use clicks/ like a buzzer
		poll.setBounds(10, 300, 100, 20);
		poll.addActionListener(this);  // calls actionPerformed of this class
		window.add(poll);
		
		submit = new JButton("Submit");  // button to submit their answer
		submit.setBounds(200, 300, 100, 20);
		submit.addActionListener(this);  // calls actionPerformed of this class
		window.add(submit);
		
		
		window.setSize(400,400);
		window.setBounds(50, 50, 400, 400);
		window.setLayout(null);
		window.setVisible(true);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setResizable(false);
		
		// Attempt TCP connection
		try 
		{
			// Establish TCP connection
			tcpSocket = new Socket(hostIP, portNumber);
			System.out.println("Connection accepted");
			
			// Receive clientID from server
			ObjectInputStream reader = new ObjectInputStream(tcpSocket.getInputStream());
			clientID = reader.readInt();
			System.out.println("ClientID: " + clientID);
			
			// Close connection
			reader.close();
			tcpSocket.close();
		}
		catch (Exception e) 
		{
			System.err.println("Couldn't get I/O for the connection");
			e.printStackTrace();
			System.exit(1);
		}
		
		// Attempt UDP connection
		try 
		{			
			// Create a DatagramSocket for sending and receiving UDP packets
            udpSocket = new DatagramSocket();

            // Get the IP address of the server
            serverAddress = InetAddress.getByName(hostIP);

            // Close the socket
//            udpSocket.close();
        } 
		catch (Exception e) 
		{
            System.err.println("Error establishing UDP connection");
			e.printStackTrace();
        }
	}
	
	// Validate the IP address format
    private static boolean isValidIPAddress(String ipAddress) 
    {
        // Regular expression to validate format
    	String ipPattern = "^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){3}"
        		+ "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$";
        
    	return ipAddress.matches(ipPattern);
    }

    // Validate the port number range
    private static boolean isValidPortNumber(String portNumber) 
    {
    	String numbersRegex = "^[0-9]+$";
    	
    	// Ensure port number contains only numbers
    	if(portNumber.matches(numbersRegex))
    	{
    		int port = Integer.valueOf(portNumber);
    		
    		// Ensure port number is in the range 1024-65535
        	return (1024 <= port && port <= 65535);
    	}
    	
    	// Invalid port number
    	else
    		return false;
    }

	// this method is called when you check/uncheck any radio button
	// this method is called when you press either of the buttons- submit/poll
	@Override
	public void actionPerformed(ActionEvent e)
	{
		System.out.println("You clicked " + e.getActionCommand());
		
		// input refers to the radio button you selected or button you clicked
		String input = e.getActionCommand();  
		switch(input)
		{
			case "Option 1":	// Your code here
								break;
			case "Option 2":	// Your code here
								break;
			case "Option 3":	// Your code here
								break;
			case "Option 4":	// Your code here
								break;
			case "Poll":
				try 
				{
		            // Send clientID to the server
		            String id = String.valueOf(clientID);
		            byte[] sendData = id.getBytes();
		            
		            // Create a DatagramPacket to send the clientID data to the server
		            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, portNumber);
		            
		            // Send packet to the server
		            udpSocket.send(sendPacket);
		            System.out.println("Sent buzz to server");
				} 
				catch (Exception e2) 
				{
					System.err.println("Error sending UDP packet");
					e2.printStackTrace();
				}
				
				break;
								
			case "Submit":		// Your code here
								break;
			default:
								System.out.println("Incorrect Option");
		}
		
		// test code below to demo enable/disable components
		// DELETE THE CODE BELOW FROM HERE***
//		if(poll.isEnabled())
//		{
//			poll.setEnabled(false);
//			submit.setEnabled(true);
//		}
//		else
//		{
//			poll.setEnabled(true);
//			submit.setEnabled(false);
//		}
//		
//		question.setText("Q2. This is another test problem " + random.nextInt());
//		
//		// you can also enable disable radio buttons
//		options[random.nextInt(4)].setEnabled(false);
//		options[random.nextInt(4)].setEnabled(true);
//		// TILL HERE ***
		
	}
	
	// this class is responsible for running the timer on the window
	public class TimerCode extends TimerTask
	{
		private int duration;  // write setters and getters as you need
		public TimerCode(int duration)
		{
			this.duration = duration;
		}
		@Override
		public void run()
		{
			if(duration < 0)
			{
				timer.setText("Timer expired");
				window.repaint();
				this.cancel();  // cancel the timed task
				return;
				// you can enable/disable your buttons for poll/submit here as needed
			}
			
			if(duration < 6)
				timer.setForeground(Color.red);
			else
				timer.setForeground(Color.black);
			
			timer.setText("TIME: " + duration);
			duration--;
			window.repaint();
		}
	}
	
}