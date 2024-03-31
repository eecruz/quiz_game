import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class test
{ 
     
   
    //below is the first attempt. store text lines into an array, and index as neeced (mroe hard coded approach) 
    //START
    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("q2.txt");
        Scanner scanner = new Scanner (new FileInputStream("q2.txt"));
        int line = 0; 
        String first [] = new String[7];
        while(scanner.hasNext() && line < first.length)
        { 
           first[line] = scanner.nextLine(); 
           line++;
        //System.out.println(line + "_" + scanner.nextLine());
          //      line++;
        } 

        int serverNum = 0; 
        serverNum = Integer.parseInt(first[0]); 
        System.out.println(serverNum);
        String question; 
        question = first[2]; 
        System.out.println(question);
        String option1;
        String option2;
        String option3;
        String option4;
    } 
    //END
    
    // public static void main(String[] args) throws FileNotFoundException {
    //     File file = new File("words.txt");
    //     Scanner scanner = new Scanner (new FileInputStream("words.txt"));
    //     int line = 0; 
    //     String first [] = new String[7];
    //     while(scanner.hasNext() && line < first.length)
    //     { 
    //        first[line] = scanner.nextLine(); 
    //        line++;
    //     //System.out.println(line + "_" + scanner.nextLine());
    //       //      line++;
    //     } 

    //     String question; 
    //     question = first[2]; 
    //     System.out.println(question);
    //     String option1;
    //     String option2;
    //     String option3;
    //     String option4;
    //  }
    




}