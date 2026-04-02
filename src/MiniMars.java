import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

public class MiniMars {
    public static void main(String[] args) {
        HashSet<String> r_type = new HashSet<String>(
                Arrays.asList("AND", "OR", "ADD", "SUB", "SLT", "MUL", "DIV", "NOR"
                ));
        HashSet<String> i_type = new HashSet<String>(
                Arrays.asList("ADDI", "LB", "SB", "BNE", "BEQ")
        );
        HashSet<String> pseudo = new HashSet<String>(
                Arrays.asList("NOP", "MOVE", "SLL", "LA", "LI", "ADDI", "STOP")
        );

        for (String arg : args) {
            ArrayList<ArrayList<String>> tokenized_lines = new ArrayList<>();

            // Lexer: converts lines to tokens
            try (BufferedReader br = new BufferedReader(new FileReader(arg))) {
                String line;

                lineLoop:
                while ((line = br.readLine()) != null) {
                    ArrayList<String> tokens = new ArrayList<>();
                    StringBuilder token = new StringBuilder();

                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);

                        // If a comment is encountered, jump to the next line
                        if (c == '#') {
                            if (!token.isEmpty()) tokens.add(token.toString());
                            continue lineLoop;
                        }

                        // If an unwanted character is encountered (whitespace, parentheses, or commas), skip it
                        if (Character.isWhitespace(c) || c == ',' || c == '(' || c == ')') {
                            if (!token.isEmpty()) {
                                tokens.add(token.toString());
                                token = new StringBuilder();
                            }
                        } else {
                            token.append(c);
                        }
                    }
                    if (!token.isEmpty()) tokens.add(token.toString());
                    if (!tokens.isEmpty()) tokenized_lines.add(tokens);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
//            for(ArrayList<String> tokens : tokenized_lines) {
//                System.out.print("[");
//                for(String token : tokens) {
//                    System.out.print(token + ", ");
//                }
//                System.out.println("] " + tokens.size());
//            }
        }
    }
}