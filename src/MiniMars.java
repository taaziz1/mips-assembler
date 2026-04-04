import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * MiniMars Assembler for 8-bit Mini-MIPS ISA.
 * This class processes assembly text files and converts them into 16-bit hex
 * machine code.
 * 
 * @author Jiaqiao Han & Talha Aziz
 * @version 04/04/2026
 */
public class MiniMars {
    private static final HashSet<String> R_TYPE = new HashSet<>(
            Arrays.asList("AND", "OR", "ADD", "SUB", "SLT", "MUL", "DIV", "NOR"));
    private static final HashSet<String> I_TYPE = new HashSet<>(
            Arrays.asList("ADDI", "LB", "SB", "BNE", "BEQ"));
    private static final HashSet<String> PSEUDO = new HashSet<>(
            Arrays.asList("NOP", "MOVE", "SLL", "LA", "LI", "STOP"));

    private enum Section {
        NONE, DATA, TEXT
    }

    private static class ParsedLine {
        int lineNo;
        Section section;
        List<String> labels = new ArrayList<>();
        String op;
        List<String> args = new ArrayList<>();
        boolean directive;
        String raw;
    }

    private static class DataByte {
        int address;
        int value;

        DataByte(int address, int value) {
            this.address = address;
            this.value = value;
        }
    }

    private static final Map<String, Integer> OPCODES = new HashMap<>();
    static {
        OPCODES.put("AND", 0x0);
        OPCODES.put("OR", 0x1);
        OPCODES.put("ADD", 0x2);
        OPCODES.put("ADDI", 0x3);
        OPCODES.put("LB", 0x4);
        OPCODES.put("SB", 0x5);
        OPCODES.put("SUB", 0x6);
        OPCODES.put("SLT", 0x7);
        OPCODES.put("MUL", 0x8);
        OPCODES.put("DIV", 0x9);
        OPCODES.put("NOR", 0xC);
        OPCODES.put("BNE", 0xD);
        OPCODES.put("BEQ", 0xE);
        OPCODES.put("J", 0xF);
    }

    /**
     * Converts a decimal string to a binary string.
     * 
     * @param f      The decimal string that needs to convert.
     * @param length The desired length of the resulting binary string.
     * @return A binary string of the specified length.
     */
    public static String decimalStringToBinaryString(String f, int length) {
        if (f.equals("ZERO"))
            return "0".repeat(length);
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

    /**
     * Entry point for the MiniMars assembler.
     * Processes each file path provided in the command line arguments.
     * 
     * @param args Array of input file paths to be assembled.
     * @throws Exception If an error occurs during file reading or writing.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0)
            return;
        for (String input : args) {
            assembleFile(input);
        }
    }

    private static void assembleFile(String inputPath) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(inputPath));
        List<ParsedLine> parsed = parseAll(lines);

        Map<String, Integer> symbols = new LinkedHashMap<>();
        List<DataByte> dataBytes = new ArrayList<>();

        int initInstrCount = firstPassData(parsed, symbols, dataBytes);
        resolveTextLabels(parsed, symbols, initInstrCount);

        List<Integer> machine = new ArrayList<>();

        for (DataByte db : dataBytes) {
            emitLI(machine, 1, db.value);
            machine.add(encodeI("SB", 0, 1, db.address));
        }

        int pc = machine.size();
        for (ParsedLine pl : parsed) {
            if (pl.section != Section.TEXT || pl.directive || pl.op == null)
                continue;
            pc = emitSourceInstruction(machine, pl, pc, symbols);
        }

        StringBuilder out = new StringBuilder();
        out.append("v2.0 raw\n");
        for (int i = 0; i < machine.size(); i++) {
            if (i > 0)
                out.append(' ');
            out.append(String.format("%04X", machine.get(i) & 0xFFFF));
        }
        out.append('\n');

        Path outPath = Paths.get(inputPath + ".asm");
        Files.writeString(outPath, out.toString());
    }

    private static List<ParsedLine> parseAll(List<String> lines) {
        List<ParsedLine> result = new ArrayList<>();
        Section current = Section.NONE;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String s = stripComment(raw).trim();
            if (s.isEmpty())
                continue;

            ParsedLine pl = new ParsedLine();
            pl.lineNo = i + 1;
            pl.raw = raw;

            while (true) {
                Matcher m = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.*)$").matcher(s);
                if (!m.matches())
                    break;
                pl.labels.add(m.group(1));
                s = m.group(2).trim();
                if (s.isEmpty())
                    break;
            }

            if (s.isEmpty()) {
                pl.section = current;
                result.add(pl);
                continue;
            }

            String upper = s.toUpperCase(Locale.ROOT);
            if (upper.equals(".DATA")) {
                current = Section.DATA;
                pl.section = current;
                pl.directive = true;
                pl.op = ".DATA";
                result.add(pl);
                continue;
            }
            if (upper.equals(".TEXT")) {
                current = Section.TEXT;
                pl.section = current;
                pl.directive = true;
                pl.op = ".TEXT";
                result.add(pl);
                continue;
            }

            pl.section = current;
            int sp = findFirstWhitespace(s);
            if (sp < 0) {
                pl.op = s;
            } else {
                pl.op = s.substring(0, sp).trim();
                String argStr = s.substring(sp).trim();
                if (!argStr.isEmpty())
                    pl.args = splitArgs(argStr);
            }
            pl.directive = pl.op.startsWith(".");
            result.add(pl);
        }
        return result;
    }

    private static int firstPassData(List<ParsedLine> parsed, Map<String, Integer> symbols, List<DataByte> dataBytes) {
        int dataAddr = 0;
        int initInstrCount = 0;
        for (ParsedLine pl : parsed) {
            if (pl.section != Section.DATA)
                continue;
            for (String label : pl.labels) {
                putSymbol(symbols, label, dataAddr, pl.lineNo);
            }
            if (pl.op == null)
                continue;
            String op = pl.op.toUpperCase(Locale.ROOT);
            if (op.equals(".SPACE")) {
                requireArgs(pl, 1);
                dataAddr += parseNumber(pl.args.get(0));
            } else if (op.equals(".BYTE")) {
                for (String a : pl.args) {
                    int val = parseNumber(a);
                    dataBytes.add(new DataByte(dataAddr, val));
                    initInstrCount += liLength(val) + 1;
                    dataAddr += 1;
                }
            }
        }
        return initInstrCount;
    }

    private static void resolveTextLabels(List<ParsedLine> parsed, Map<String, Integer> symbols, int textStart) {
        for (int iter = 0; iter < 20; iter++) {
            boolean changed = false;
            int pc = textStart;
            for (ParsedLine pl : parsed) {
                if (pl.section != Section.TEXT)
                    continue;
                for (String label : pl.labels) {
                    Integer old = symbols.get(label);
                    if (old == null || old.intValue() != pc) {
                        symbols.put(label, pc);
                        changed = true;
                    }
                }
                if (pl.directive || pl.op == null)
                    continue;
                pc += sourceLength(pl, symbols);
            }
            if (!changed)
                return;
        }
    }

    private static int sourceLength(ParsedLine pl, Map<String, Integer> symbols) {
        String op = pl.op.toUpperCase(Locale.ROOT);
        if (op.equals("NOP") || op.equals("MOVE") || op.equals("STOP"))
            return 1;
        if (op.equals("SLL"))
            return 1 + parseNumber(pl.args.get(2));
        if (op.equals("LA")) {
            Integer val = resolveLabelOrNumber(pl.args.get(1), symbols);
            return (val != null && fitsSigned6(val)) ? 1 : 4;
        }
        if (op.equals("LI"))
            return liLength(parseNumber(pl.args.get(1)));
        if (op.equals("ADDI"))
            return fitsSigned6(parseNumber(pl.args.get(2))) ? 1 : 5;
        return 1;
    }

    private static int emitSourceInstruction(List<Integer> machine, ParsedLine pl, int pc,
            Map<String, Integer> symbols) {
        String op = pl.op.toUpperCase(Locale.ROOT);
        switch (op) {
            case "NOP":
                machine.add(encodeR("AND", 0, 0, 0, 0));
                return pc + 1;
            case "MOVE": {
                int dest = parseRegister(pl.args.get(0));
                int src = parseRegister(pl.args.get(1));
                machine.add(encodeR("ADD", 0, src, dest, 0));
                return pc + 1;
            }
            case "SLL": {
                int dest = parseRegister(pl.args.get(0));
                int src = parseRegister(pl.args.get(1));
                int n = parseNumber(pl.args.get(2));
                machine.add(encodeR("ADD", 0, src, dest, 0));
                for (int i = 0; i < n; i++)
                    machine.add(encodeR("ADD", dest, dest, dest, 0));
                return pc + 1 + n;
            }
            case "LA": {
                int dest = parseRegister(pl.args.get(0));
                int val = resolveLabelOrNumber(pl.args.get(1), symbols);
                return emitLA(machine, dest, val, pc);
            }
            case "LI": {
                int dest = parseRegister(pl.args.get(0));
                int n = parseNumber(pl.args.get(1));
                emitLI(machine, dest, n);
                return pc + liLength(n);
            }
            case "ADDI": {
                int dest = parseRegister(pl.args.get(0));
                int src = parseRegister(pl.args.get(1));
                int n = parseNumber(pl.args.get(2));
                if (fitsSigned6(n)) {
                    machine.add(encodeI("ADDI", src, dest, n));
                    return pc + 1;
                }
                machine.add(encodeI("ADDI", 0, dest, n / 4));
                machine.add(encodeR("ADD", dest, dest, dest, 0));
                machine.add(encodeR("ADD", dest, dest, dest, 0));
                machine.add(encodeI("ADDI", dest, dest, n % 4));
                machine.add(encodeR("ADD", src, dest, dest, 0));
                return pc + 5;
            }
            case "STOP":
                machine.add(encodeI("BEQ", 7, 7, -1));
                return pc + 1;
            default:
                machine.add(encodeReal(pl, pc, symbols));
                return pc + 1;
        }
    }

    private static int emitLA(List<Integer> machine, int dest, int value, int pc) {
        if (fitsSigned6(value)) {
            machine.add(encodeI("ADDI", 0, dest, value));
            return pc + 1;
        }
        machine.add(encodeI("ADDI", 0, dest, value / 4));
        machine.add(encodeR("ADD", dest, dest, dest, 0));
        machine.add(encodeR("ADD", dest, dest, dest, 0));
        machine.add(encodeI("ADDI", dest, dest, value % 4));
        return pc + 4;
    }

    private static void emitLI(List<Integer> machine, int dest, int n) {
        if (fitsSigned6(n)) {
            machine.add(encodeI("ADDI", 0, dest, n));
            return;
        }
        machine.add(encodeI("ADDI", 0, dest, n / 4));
        machine.add(encodeR("ADD", dest, dest, dest, 0));
        machine.add(encodeR("ADD", dest, dest, dest, 0));
        machine.add(encodeI("ADDI", dest, dest, n % 4));
    }

    private static int liLength(int n) {
        return fitsSigned6(n) ? 1 : 4;
    }

    private static int encodeReal(ParsedLine pl, int pc, Map<String, Integer> symbols) {
        String op = pl.op.toUpperCase(Locale.ROOT);
        if (R_TYPE.contains(op)) {
            int rd = parseRegister(pl.args.get(0));
            int rs = parseRegister(pl.args.get(1));
            int rt = parseRegister(pl.args.get(2));
            return encodeR(op, rs, rt, rd, 0);
        } else if (op.equals("LB") || op.equals("SB")) {
            int rt = parseRegister(pl.args.get(0));
            int[] mem = parseMemOperand(pl.args.get(1));
            return encodeI(op, mem[1], rt, mem[0]);
        } else if (op.equals("BNE") || op.equals("BEQ")) {
            int rs = parseRegister(pl.args.get(0));
            int rt = parseRegister(pl.args.get(1));
            Integer lab = resolveLabelOrNumber(pl.args.get(2), symbols);
            int imm = isPureNumber(pl.args.get(2)) ? lab : lab - (pc + 1);
            return encodeI(op, rs, rt, imm);
        } else if (op.equals("J")) {
            return encodeJ(op, resolveLabelOrNumber(pl.args.get(0), symbols));
        }
        throw new IllegalArgumentException("Unsupported: " + op);
    }

    private static int encodeR(String op, int rs, int rt, int rd, int shamt) {
        return ((getOpcode(op) & 0xF) << 12) | ((rs & 0x7) << 9) | ((rt & 0x7) << 6) | ((rd & 0x7) << 3)
                | (shamt & 0x7);
    }

    private static int encodeI(String op, int rs, int rt, int imm) {
        return ((getOpcode(op) & 0xF) << 12) | ((rs & 0x7) << 9) | ((rt & 0x7) << 6) | (imm & 0x3F);
    }

    private static int encodeJ(String op, int target) {
        return ((getOpcode(op) & 0xF) << 12) | (target & 0x0FFF);
    }

    private static int getOpcode(String op) {
        return OPCODES.get(op.toUpperCase(Locale.ROOT));
    }

    private static int parseRegister(String token) {
        String name = token.trim().substring(1).toLowerCase(Locale.ROOT);
        switch (name) {
            case "0":
            case "zero":
                return 0;
            case "1":
            case "at":
                return 1;
            case "2":
            case "v0":
                return 2;
            case "3":
            case "v1":
                return 3;
            case "4":
            case "a0":
                return 4;
            case "5":
            case "a1":
                return 5;
            case "6":
            case "a2":
                return 6;
            case "7":
            case "a3":
            case "ra":
                return 7;
            default:
                throw new IllegalArgumentException("Reg: " + token);
        }
    }

    private static int[] parseMemOperand(String s) {
        Matcher m = Pattern.compile("^(.+)\\((\\$[0-7])\\)$").matcher(s.replaceAll("\\s+", ""));
        m.matches();
        return new int[] { parseNumber(m.group(1)), parseRegister(m.group(2)) };
    }

    private static int parseNumber(String s) {
        return Integer.decode(s.trim().startsWith("+") ? s.trim().substring(1) : s.trim());
    }

    private static boolean fitsSigned6(int x) {
        return x >= -32 && x <= 31;
    }

    private static Integer resolveLabelOrNumber(String token, Map<String, Integer> symbols) {
        token = token.trim();
        return isPureNumber(token) ? parseNumber(token) : symbols.get(token);
    }

    private static boolean isPureNumber(String s) {
        try {
            Integer.decode(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripComment(String s) {
        int p = s.indexOf('#');
        return p >= 0 ? s.substring(0, p) : s;
    }

    private static int findFirstWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i)))
                return i;
        }
        return -1;
    }

    private static List<String> splitArgs(String s) {
        List<String> raw = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int paren = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(')
                paren++;
            if (c == ')')
                paren--;
            if (paren == 0 && (c == ',' || Character.isWhitespace(c))) {
                if (cur.length() > 0)
                    raw.add(cur.toString().trim());
                cur.setLength(0);
            } else
                cur.append(c);
        }
        if (cur.length() > 0)
            raw.add(cur.toString().trim());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (i + 1 < raw.size() && raw.get(i + 1).startsWith("(")) {
                out.add(raw.get(i) + raw.get(i + 1));
                i++;
            } else
                out.add(raw.get(i));
        }
        return out;
    }

    private static void requireArgs(ParsedLine pl, int n) {
        if (pl.args.size() != n)
            throw new IllegalArgumentException("Args error line " + pl.lineNo);
    }

    private static void putSymbol(Map<String, Integer> symbols, String label, int value, int lineNo) {
        symbols.put(label, value);
    }
}
