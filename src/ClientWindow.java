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
import java.net.ConnectException;
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
	private JLabel alertLabel;
	private JLabel waitingLabel;
	private JLabel question;
	private JLabel timer;
	private JLabel scoreLabel;

	// Other
	private TimerTask clock;	
	private JFrame window;
	private int clientID;
	private String userAnswer;
	private int score;

	// write setters and getters as you need

	public ClientWindow() throws FileNotFoundException
	{
		// Initial user prompt
		JOptionPane.showMessageDialog(window, "This is a trivia game", 
				"Trivia Game", JOptionPane.PLAIN_MESSAGE);

		hostIP = null;
		clientID = -1;

		// Prevent user from continuing without providing valid host IP address
		while(true) 
		{
			// Capture input IP from user
			hostIP = JOptionPane.showInputDialog(window, "Please enter the IP address of the host machine (server):", 
					"Trivia Game", JOptionPane.PLAIN_MESSAGE);

			// Trim spaces from input
			if(hostIP != null)
				hostIP = hostIP.trim();

			// User clicked the "Cancel" button, close the entire application
			else    
				System.exit(0);

			// Valid IP address provided, break out of the loop
			if(isValidIPAddress(hostIP))
			{
				try 
				{
					serverAddress = InetAddress.getByName(hostIP);
				} 
				catch(UnknownHostException e) 
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
		while(true) 
		{
			// Capture input port number from user
			String port = JOptionPane.showInputDialog(window, 
					"Please enter the port number used by the host machine (server):", "Trivia Game", JOptionPane.PLAIN_MESSAGE);

			// Trim spaces from input
			if(port != null)
				port = port.trim();

			// User clicked the "Cancel" button, close the entire application
			else    
				System.exit(0);

			// Valid port number provided, break out of the loop
			if(isValidPortNumber(port))
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

		// Connection could not be established
		catch(ConnectException e)
		{
			JOptionPane.showMessageDialog(window, "Couldn't connect to server. Please ensure "
					+ "IP address and port number are correct.", "Can't Find Server", JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		} 
		
		// I/O errors
		catch(IOException e) 
		{
			JOptionPane.showMessageDialog(window, "An unexpected error occured. Please try again later.", 
					"Error", JOptionPane.ERROR_MESSAGE);
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
		catch(Exception e) 
		{
			System.err.println("Error establishing UDP connection");
			e.printStackTrace();
		}

		// Game window
		window = new GameFrame();

		// Create temporary screen before game starts
		waitingLabel = new JLabel("Waiting for game to start...");
		window.add(waitingLabel);
		waitingLabel.setBounds(10, 5, 350, 20);

		// Wait for server to start game
		try 
		{
			Object input = reader.readObject();

			// Game is already in progress
			if(input instanceof String && input.equals("wait"))
			{
				System.out.println("Game in progress. Waiting for next question...");
				waitingLabel.setText("Game in progress. Please wait for next question...");

				// Wait for next question from server
				while((input = reader.readObject()) instanceof String)
				{
					if(input.equals("next"))
					{
						waitingLabel.setVisible(false);
						break;
					}					
				}	
			}

			// Server starts game
			else if(input instanceof String && input.equals("start"))
			{
				System.out.println("Starting game...");
				waitingLabel.setVisible(false);
			}
		} 

		catch(ClassNotFoundException e) 
		{
			System.err.println("ERROR starting game: ClassNotFound");
			e.printStackTrace();
		} 

		// Client closed before game started
		catch(IOException e) 
		{
			// Do nothing
		}

		// Label for alerting client of missing answer
		alertLabel = new JLabel();
		alertLabel.setBounds(10, 325, 350, 20);
		alertLabel.setVisible(false);
		window.add(alertLabel);
		
		question = new JLabel(); // represents the question
		window.add(question);
		question.setBounds(10, 5, 600, 100);  

		// Option radio buttons
		options = new JRadioButton[4];
		optionGroup = new ButtonGroup();
		
		for(int index=0; index<options.length; index++)
		{
			options[index] = new JRadioButton("Option " + (index+1));  // represents an option
			// if a radio button is clicked, the event would be thrown to this class to handle
			options[index].addActionListener(this);
			options[index].setBounds(10, 110+(index*20), 600, 20);
			options[index].setEnabled(false);
			window.add(options[index]);
			optionGroup.add(options[index]);
		}
		
		userAnswer = "";

		timer = new JLabel("TIMER");  // represents the countdown shown on the window
		timer.setBounds(250, 250, 100, 20);
		clock = new TimerCode(15, true, null);  // represents clocked task that should run after X seconds
		Timer t = new Timer();  // event generator
		t.schedule(clock, 0, 1000); // clock is called every second
		window.add(timer);


		scoreLabel = new JLabel("SCORE: " + score); // represents the score
		scoreLabel.setBounds(50, 250, 100, 20);
		window.add(scoreLabel);

		poll = new JButton("Poll");  // button that use clicks/ like a buzzer
		poll.setBounds(10, 300, 100, 20);
		poll.addActionListener(this);  // calls actionPerformed of this class
		window.add(poll);

		submit = new JButton("Submit");  // button to submit their answer
		submit.setBounds(200, 300, 100, 20);
		submit.setEnabled(false);
		submit.addActionListener(this);  // calls actionPerformed of this class
		window.add(submit);

		Object input;
		
		// Continually read and display questions from server
		try
		{
			// While socket is still open
			while((input = reader.readObject()) != null) 
			{
				// Process the file and display question
				if(input instanceof File) 
				{
					String[] questionInfo = new String[5];
					File tempFile = (File) input;
					Scanner scanner = new Scanner (new FileInputStream(tempFile));
					int index = 0;

					// Read values from file
					while(scanner.hasNext() && index < questionInfo.length)
					{
						questionInfo[index] = scanner.nextLine().trim();
						index++;
					}

					// Display questions and answers from file
					question.setText(questionInfo[0]);
					for (int i = 0; i <= 3; i++)
					{
						options[i].setText(questionInfo[i + 1]);
					}

					clock = new TimerCode(15, true, null);
					t.schedule(clock, 0, 1000); // clock is called every second
				}
				
				else if(input instanceof String)
				{										
					// Ready client for next question
					if(((String)input).equals("next"))
					{
						poll.setEnabled(true);
						alertLabel.setVisible(false);
					}

					// This client was the first to poll
					else if(input.equals("ack"))
					{
						alertLabel.setForeground(new Color(54, 102, 0)); // Dark green
						alertLabel.setText("You had the fastest poll! Answer before the timer runs out!");
						alertLabel.setVisible(true);
						toggleButtons();
						clock = new TimerCode(10, false, true);  // represents clocked task that should run after X seconds
						t.schedule(clock, 0, 1000); // clock is called every second

					}
					
					// This client was late in polling
					else if(input.equals("negative-ack"))
					{
						alertLabel.setForeground(Color.RED);
						alertLabel.setText("You were late polling! Better luck on the next question...");
						alertLabel.setVisible(true);
						clock = new TimerCode(10, false, false);  // represents clocked task that should run after X seconds
						t.schedule(clock, 0, 1000); // clock is called every second
					}
					
					// No clients polled
					else if(input.equals("no-poll"))
					{
						alertLabel.setForeground(Color.BLACK);
						alertLabel.setText("No players polled this round! On to the next question...");
						alertLabel.setVisible(true);
					}
					
					// This client answered correctly
					else if(input.equals("correct"))
					{
						score += 10;
						scoreLabel.setText("SCORE: " + score);
						scoreLabel.repaint();
						alertLabel.setForeground(new Color(54, 102, 0)); // Dark green
						alertLabel.setText("You answered correctly! Keep it up!");
						alertLabel.setVisible(true);
					}
					
					// This client answered incorrectly
					else if(input.equals("incorrect"))
					{
						score -= 10;
						scoreLabel.setText("SCORE: " + score);
						scoreLabel.repaint();
						alertLabel.setForeground(Color.RED);
						alertLabel.setText("You answered wrong! Get your head in the game!");
						alertLabel.setVisible(true);
					}
					
					// This client polled but didn't answer
					else if(input.equals("penalty"))
					{
						score -= 20;
						scoreLabel.setText("SCORE: " + score);
						scoreLabel.repaint();
						alertLabel.setForeground(Color.RED);
						alertLabel.setText("You didn't submit anything! Did you even know the answer?");
						alertLabel.setVisible(true);
						toggleButtons();
					}
					
					// Answering client (not this client) answered correctly
					else if(((String)input).contains("alt_correct"))
					{
						// Get ID of client who attempted to answer question
						int ackClientID = Integer.parseInt(((String)input).replaceAll("[^0-9]", ""));
						
						// Update alert label
						alertLabel.setForeground(Color.BLUE);
						alertLabel.setText("Client " + ackClientID + " answered correctly! You need to catch up!");
						alertLabel.setVisible(true);
					}
					
					// Answering client (not this one) answered incorrectly
					else if(((String)input).contains("alt_incorrect"))
					{
						// Get ID of client who attempted to answer question
						int ackClientID = Integer.parseInt(((String)input).replaceAll("[^0-9]", ""));
						
						// Update alert label
						alertLabel.setForeground(Color.BLUE);
						alertLabel.setText("Client " + ackClientID + " answered wrong! Definitely a skill issue...");
						alertLabel.setVisible(true);
					}
					
					// Answering client (not this one) polled but did not answer
					else if(((String)input).contains("alt_penalty"))
					{
						// Get ID of client who attempted to answer question
						int ackClientID = Integer.parseInt(((String)input).replaceAll("[^0-9]", ""));
						
						// Update alert label
						alertLabel.setForeground(Color.BLUE);
						alertLabel.setText("Client " + ackClientID + " didn't answer! Why did they even poll? -_-");
						alertLabel.setVisible(true);
					}
					
					// Game has finished (this client did not win)
					else if(input.equals("end"))
					{
						JOptionPane.showMessageDialog(window, "Game has finished! You scored: " + score,
								"Game Finished", JOptionPane.PLAIN_MESSAGE);
						System.exit(0);
					}
					
					// Game has finished (this client won)
					else if(input.equals("win"))
					{
						JOptionPane.showMessageDialog(window, "You won! You scored: " + score,
								"Winner!!!", JOptionPane.PLAIN_MESSAGE);
						System.exit(0);
					}
				}
			}
		}

		catch(ClassNotFoundException e) 
		{
			System.err.println("ERROR loading question: ClassNotFound");
			e.printStackTrace();
		} 

		catch(FileNotFoundException e) 
		{
			System.err.println("ERROR loading question: FileNotFound");
			e.printStackTrace();
		} 

		catch(IOException e) 
		{
			// Client closed before receiving question
			// Do nothing
		}

		// If server stops running
		// Stop timer
		clock.cancel();
		
		// Show alert to user
		JOptionPane.showMessageDialog(window, "Lost connection to server. Exiting game...", 
				"Connection Terminated", JOptionPane.PLAIN_MESSAGE);
		
		// Exit game
		System.exit(0);
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
	
	// Toggle submit and option buttons enabled/disabled
	public void toggleButtons()
	{
		for (JRadioButton button : options)
		{
			button.setEnabled(!button.isEnabled());
			button.setSelected(false);
		}
		
		submit.setEnabled(!submit.isEnabled());
	}
	
	// Write string to server using TCP
	public void writeToServerTCP(String str) 
	{
		try 
		{
			// Send kill request on close
			writer.writeObject(str);
			writer.flush();
		} 
		
		catch(IOException e1) 
		{
			System.err.println("ERROR writing " + str + " to server");
			e1.printStackTrace();
		}
	}
	
	// Write an integer to the server using UDP
	public void writeToServerUDP(int x)
	{
		try 
		{
			String xString;
			
			// Convert integer to string (deals with weird transmission error)
			if(0 < x && x < 10)
				xString = "0" + String.valueOf(x);
			else
				xString = String.valueOf(x);
						
			// Convert string to byte array
			byte[] sendData = xString.getBytes();

			// Create a DatagramPacket to send the clientID data to the server
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, portNumber);

			// Send packet to the server
			udpSocket.send(sendPacket);
						
			if(x == clientID)
				System.out.println("Sent buzz to server");
		} 
		catch(Exception e2) 
		{
			System.err.println("Error sending UDP packet");
			e2.printStackTrace();
		}
	}

	// this method is called when you check/uncheck any radio button
	// this method is called when you press either of the buttons- submit/poll
	@Override
	public void actionPerformed(ActionEvent e)
	{		
		// User clicks poll
		if(e.getSource().equals(poll))
		{
			// Send clientID to the server
			writeToServerUDP(clientID);
		}
		
		// Submit user answer to server if they click submit
		else if(e.getSource().equals(submit))
		{			
			// Obtain selected answer as string and alert user of submission
			if(options[0].isSelected())
			{
				userAnswer = options[0].getText();
				alertLabel.setForeground(Color.BLACK);
				alertLabel.setText("Submitted: Option 1");
				alertLabel.setVisible(true);
				toggleButtons();
			}
			
			else if(options[1].isSelected())
			{
				userAnswer = options[1].getText();
				alertLabel.setForeground(Color.BLACK);
				alertLabel.setText("Submitted: Option 2");
				alertLabel.setVisible(true);
				toggleButtons();
			}
			
			else if(options[2].isSelected())
			{
				userAnswer = options[2].getText();
				alertLabel.setForeground(Color.BLACK);
				alertLabel.setText("Submitted: Option 3");
				alertLabel.setVisible(true);
				toggleButtons();
			}
			
			else if(options[3].isSelected())
			{
				userAnswer = options[3].getText();
				alertLabel.setForeground(Color.BLACK);
				alertLabel.setText("Submitted: Option 4");
				alertLabel.setVisible(true);
				toggleButtons();
			}
			
			else // No answer provided
			{
				alertLabel.setForeground(Color.RED);
				alertLabel.setText("*Please select an answer before you submit!*");
				alertLabel.setVisible(true);
			}
		}
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

					// Send kill request on close
					writeToServerTCP("kill");

					System.out.println("Game closed");
					System.exit(0);
				}
			});

			// Set frame properties
			setSize(400,400);
			setBounds(50, 50, 600, 400);
			setLayout(null);
			setVisible(true);
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setResizable(false);
		}
	}

	// this class is responsible for running the timer on the window
	public class TimerCode extends TimerTask
	{
		// Length of timer
		private int duration;

		// Indicates whether this timer is for polling or answering
		private Boolean isPolling;
		
		// Indicates whether this client had the fastest poll
		private Boolean fastestPoll;
		
		public TimerCode(int duration, Boolean isPolling, Boolean fastestPoll)
		{
			this.duration = duration;
			this.isPolling = isPolling;
			this.fastestPoll = fastestPoll;
		}
		
		@Override
		public void run()
		{
			if(duration < 0)
			{
				// Timer is for answering
				if(!isPolling && fastestPoll)
				{
					// Client submitted answer
					if(!userAnswer.equals(""))
						writeToServerTCP(userAnswer);
					
					// Client did not submit answer in time
					else
						writeToServerTCP("no answer");
					
					// Reset userAnswer for next question
					userAnswer = "";
				}
				
				// Timer is for polling
				else
				{
					// Signal polling complete to server
					writeToServerUDP(-1);
				}
				
				timer.setText("Times up!");
				window.repaint();
				poll.setEnabled(false);
					
				// Cancel the timed task
				this.cancel();  
				return;
			}

			if(duration <= 5)
				timer.setForeground(Color.RED);
			else
				timer.setForeground(Color.BLACK);
			
			// Update timer label
			timer.setText("TIME: " + duration);
			duration--;
			window.repaint();
		}
	}

}