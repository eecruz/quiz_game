import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.TimerTask;
import java.util.Scanner;
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

	// Connections
	private ObjectOutputStream writer;
	private ObjectInputStream reader;

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

	public ClientWindow() throws FileNotFoundException
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

		// Attempt TCP connection
		try 
		{
			// Establish TCP connection
			tcpSocket = new Socket(hostIP, portNumber);
			System.out.println("Connection accepted");

			// Initialize reader and writer
			writer = new ObjectOutputStream(tcpSocket.getOutputStream());
			reader = new ObjectInputStream(tcpSocket.getInputStream());

			// Receive clientID from server
			clientID = reader.readInt();
			System.out.println("ClientID: " + clientID);
		}
		catch (Exception e) 
		{
			System.err.println("Couldn't get I/O for the connection");
			e.printStackTrace();
			System.exit(0);
		}

		// Attempt UDP connection
		try 
		{			
			// Create a DatagramSocket for sending and receiving UDP packets
			udpSocket = new DatagramSocket();

			// Get the IP address of the server
			serverAddress = InetAddress.getByName(hostIP);
		} 
		catch (Exception e) 
		{
			System.err.println("Error establishing UDP connection");
			e.printStackTrace();
		}



		// Game window
		window = new GameFrame();

		// Create temporary screen before game starts
		JLabel waitingLabel = new JLabel("Waiting for game to start...");
		window.add(waitingLabel);
		waitingLabel.setBounds(10, 5, 350, 100);

		// Wait for server to start game
		try 
		{
			Object input = reader.readObject();

			// Game is already in progress
			if(input instanceof String && ((String)input).equals("wait"))
			{
				System.out.println("Game in progress. Waiting for next question...");
				waitingLabel.setText("Game in progress. Please wait for next question...");

				// Wait for next question from server
				input = reader.readObject();
				if(input instanceof String && ((String)input).equals("next"))
				{
					waitingLabel.setVisible(false);
					readQuestion();
				}
			}

			// Server starts game
			else if(input instanceof String && ((String)input).equals("start"))
			{
				System.out.println("Starting game...");
				waitingLabel.setVisible(false);
			}
		} 

		catch (ClassNotFoundException e) 
		{
			System.err.println("ERROR starting game: ClassNotFound");
			e.printStackTrace();
		} 

		// Client closed before game started
		catch (IOException e) 
		{
			// Do nothing
		}

		//		//here my code starts 
		//		File file = new File("q2.txt");
		//        Scanner scanner = new Scanner (new FileInputStream("q2.txt"));
		//        int line = 0; 
		//        String first [] = new String[5];
		//        while(scanner.hasNext() && line < first.length)
		//        { 
		//           first[line] = scanner.nextLine(); 
		//           line++;
		//        //System.out.println(line + "_" + scanner.nextLine());
		//          //      line++;
		//        } 

		question = new JLabel(); // represents the question
		window.add(question);
		question.setBounds(10, 5, 350, 100);  

		options = new JRadioButton[4];
		optionGroup = new ButtonGroup();
		for(int index=0; index<options.length; index++)
		{
			options[index] = new JRadioButton("Option " + (index+1));  // represents an option
			// if a radio button is clicked, the event would be thrown to this class to handle
			options[index].addActionListener(this);
			options[index].setBounds(10, 110+(index*20), 350, 20);
			options[index].setEnabled(false);
			window.add(options[index]);
			optionGroup.add(options[index]);
		}

		timer = new JLabel("TIMER");  // represents the countdown shown on the window
		timer.setBounds(250, 250, 100, 20);
		clock = new TimerCode(15);  // represents clocked task that should run after X seconds
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
		submit.setEnabled(false);
		submit.addActionListener(this);  // calls actionPerformed of this class
		window.add(submit);


		//		window.setSize(400,400);
		//		window.setBounds(50, 50, 400, 400);
		//		window.setLayout(null);
		//		window.setVisible(true);
		//		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//		window.setResizable(false);

		// Read and display questions from server
		while (true)
		{
			Object input;
			
			try 
			{
				while ((input = reader.readObject()) != null) 
				{
					if (input instanceof String && input.equals("next")) 
						break;

					System.out.println("RECEIVED QUESTION FROM SERVER");

					// Process the file
					if (input instanceof File) 
					{
						String[] strList = new String[5];	
						File tempFile = (File) input;
						Scanner scanner = new Scanner (new FileInputStream(tempFile));
						int index = 0;

						while (scanner.hasNext() && index < 5)
						{
							strList[index] = scanner.nextLine();
							index++;
						}
						
						question.setText(strList[0]);
						for (int i = 0; i <= 3; i++)
						{
							options[i].setText(strList[i + 1]);
						}
					}
				}
			} 
			
			catch (ClassNotFoundException e) 
			{
				System.err.println("ERROR loading question: ClassNotFound");
				e.printStackTrace();
			} 
			
			catch (FileNotFoundException e) 
			{
				System.err.println("ERROR loading question: FileNotFound");
				e.printStackTrace();
			} 
			
			catch (IOException e) 
			{
				// Client closed before receiving question
				// Do nothing
			}
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

	private void readQuestion() 
	{

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

	public class GameFrame extends JFrame 
	{	
		public GameFrame() 
		{
			super("Trivia");

			// Add a WindowListener to the frame
			addWindowListener(new WindowAdapter() 
			{
				@Override
				public void windowClosing(WindowEvent e) 
				{
					System.out.println("Closing client connection...");

					try 
					{
						// Send kill request on close
						writer.writeObject("kill");
						writer.flush();
					} 
					catch (IOException e1) 
					{
						System.err.println("ERROR terminating connection");
						e1.printStackTrace();
					} 

					System.out.println("Game closed");
					System.exit(1);
				}
			});

			// Set frame properties
			setSize(400,400);
			setBounds(50, 50, 400, 400);
			setLayout(null);
			setVisible(true);
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setResizable(false);
		}
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
				timer.setText("Timer expired");
				window.repaint();
				poll.setEnabled(false);
				submit.setEnabled(true);
				options[0].setEnabled(true);
				options[1].setEnabled(true);
				options[2].setEnabled(true);
				options[3].setEnabled(true);
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