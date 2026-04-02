import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class MiniMars {
    public static String decimalStringToBinaryString(String f, int length) {
        if (f.equals("ZERO")) return "0".repeat(length);
        int value = Integer.parseInt(f);

        int mask = (length == 32) ? -1 : (1 << length) - 1;
        int truncated = value & mask;

        String binary = Integer.toBinaryString(truncated);

        StringBuilder result = new StringBuilder(binary);
        while (result.length() < length) {
            result.insert(0, '0');
        }

        return result.toString();
    }

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
            HashMap<String, Integer> labels = new HashMap<>();

            // Lexer: converts lines to tokens
            try (BufferedReader br = new BufferedReader(new FileReader(arg))) {
                String line;

                while ((line = br.readLine()) != null) {
                    ArrayList<String> tokens = new ArrayList<>();
                    StringBuilder token = new StringBuilder();

                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);

                        // If an unwanted character is encountered (whitespace, parentheses, or commas), add the
                        // current token to the tokens list and start a new token
                        if (Character.isWhitespace(c) || c == ',' || c == '(' || c == ')') {
                            if (!token.isEmpty()) {
                                tokens.add(token.toString());
                                token = new StringBuilder();
                            }
                        }
                        // If a # is encountered, the rest of the line must be a comment, so we go to the next line
                        else if (c == '#') {
                            break;
                        }
                        // If a : is encountered, the characters before it could be a label, so we add it to labels map
                        else if (c == ':') {
                            String t = token.toString();
                            labels.put(t, tokenized_lines.size());
                            tokens.add(t);
                            token = new StringBuilder();
                        }
                        // In all other cases, we want to add the character to the end of the current token
                        else {
                            token.append(Character.toUpperCase(c));
                        }
                    }
                    if (!token.isEmpty()) tokens.add(token.toString());
                    if (!tokens.isEmpty()) tokenized_lines.add(tokens);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Create output file
            String outputPath = "output/" + arg + ".asm";
            try {
                File outputFile =  new File(outputPath);
                outputFile.delete();
                if (!outputFile.createNewFile()) {
                    System.out.println("Output file could not be created: " + outputPath);
                    continue;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Assembly to machine code translator and file writer
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
                bw.write("v2.0 raw\n");
                boolean inText = false;
                int foundTextLabels = 0;

                for(ArrayList<String> line : tokenized_lines) {
                    if (line.isEmpty() || line.getFirst().isEmpty()) continue;

                    String first = line.getFirst();
                    if (!inText && first.equals(".TEXT")) inText = true;

                    if (inText) {
                        if (labels.containsKey(first)) {
                            foundTextLabels++;
                        } else {
                            StringBuilder hexCode = new StringBuilder();
                            StringBuilder binary =  new StringBuilder();

                            // r type translation
                            if (r_type.contains(first) && line.size() >= 4) {
                                switch (first) {
                                    case "AND":
                                        hexCode.append("0");
                                        break;
                                    case "OR":
                                        hexCode.append("1");
                                        break;
                                    case "ADD":
                                        hexCode.append("2");
                                        break;
                                    case "SUB":
                                        hexCode.append("6");
                                        break;
                                    case "SLT":
                                        hexCode.append("7");
                                        break;
                                    case "MUL":
                                        hexCode.append("8");
                                        break;
                                    case "DIV":
                                        hexCode.append("9");
                                        break;
                                    case "NOR":
                                        hexCode.append("C");
                                        break;
                                }

                                binary.append(
                                        decimalStringToBinaryString(line.get(2).substring(1), 3));
                                binary.append(
                                        decimalStringToBinaryString(line.get(3).substring(1), 3));
                                binary.append(
                                        decimalStringToBinaryString(line.get(1).substring(1), 3));
                                binary.append("000");

                                String hex_string = Integer.toHexString(Integer.parseInt(binary.toString(), 2)).toUpperCase();
                                String padded_hex_string = String.format("%" + 3 + "s", hex_string).replace(' ', '0');
                                hexCode.append(padded_hex_string);

                                bw.write(hexCode.toString() + " ");
                            }

                            // i type translation
                            else if (i_type.contains(first)) {
                                bw.write("XXXX ");
                            }

                            // j type translation
                            else if (first.equals("J")) {
                                bw.write("XXXX ");
                            }

                            // pseudo instruction translation
                            else if (pseudo.contains(first)) {
                                bw.write("XXXX ");
                            }
                        }
                    } else {
                        // data segment

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}