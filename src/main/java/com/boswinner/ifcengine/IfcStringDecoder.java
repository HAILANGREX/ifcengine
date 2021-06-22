package com.boswinner.ifcengine;


import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class IfcStringDecoder {
    private static final String codePage = "ABCDEFGHI";

    public IfcStringDecoder() {
    }

    public static String decode(String string) {
        try {
            if(string.startsWith("'"))
            	string = string.substring(1);
            if(string.endsWith("'"))
            	string = string.substring(0, string.length() - 1);
            
            CharsetDecoder decoderIso8859_1 = Charset.forName("ISO-8859-1").newDecoder();
            CharsetDecoder decoderUTF16 = Charset.forName("UTF-16BE").newDecoder();
            CharsetDecoder decoderUTF32 = Charset.forName("UTF-32").newDecoder();
            CharsetDecoder decoder = null;
            boolean extendedCodePage = false;
            String decodedString = new String();
            char[] characterArray = string.toCharArray();
            int index = -1;

            while (index < characterArray.length - 1) {
                ++index;
                int codePoint = Character.codePointAt(characterArray, index);
                if (codePoint < 32 || codePoint > 126) {
                    CharacterCodingException e = new CharacterCodingException();
                    throw e;
                }

                if (characterArray[index] == '\'') {
                    decodedString = decodedString + "'";
                    ++index;
                } else if (characterArray[index] != '\\') {
                    decodedString = decodedString.concat(new String(Character.toChars(characterArray[index])));
                } else {
                    ++index;
                    if (characterArray[index] == '\\') {
                        decodedString = decodedString + "\\";
                    } else {
                        StringBuilder var10002;
                        if (characterArray[index] == 'S') {
                            ++index;
                            if (extendedCodePage) {
                                var10002 = new StringBuilder();
                                ++index;
                                decodedString = decodedString.concat(decoder.decode(buffer(var10002.append(characterArray[index]).toString())).toString());
                            } else {
                                ++index;
                                decodedString = decodedString.concat(new String(Character.toChars(Character.codePointAt(characterArray, index) + 128)));
                            }
                        } else if (characterArray[index] == 'P') {
                            ++index;
                            int page = "ABCDEFGHI".indexOf(characterArray[index]) + 1;
                            Charset charset = Charset.forName("ISO-8859-" + page);
                            decoder = charset.newDecoder();
                            extendedCodePage = true;
                            ++index;
                        } else if (characterArray[index] == 'X') {
                            ++index;
                            int[] codePoints;
                            if (characterArray[index] == '\\') {
                                codePoints = new int[1];
                                var10002 = new StringBuilder("0x");
                                ++index;
                                var10002 = var10002.append(characterArray[index]);
                                ++index;
                                codePoints[0] = Integer.decode(var10002.append(characterArray[index]).toString());
                                decodedString = decodedString.concat(decoderIso8859_1.decode(buffer(codePoints)).toString());
                            } else if (characterArray[index] == '2') {
                                ++index;

                                do {
                                    codePoints = new int[2];
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[0] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[1] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    decodedString = decodedString.concat(decoderUTF16.decode(buffer(codePoints)).toString());
                                } while (characterArray[index + 1] != '\\');

                                index += 4;
                            } else if (characterArray[index] == '4') {
                                ++index;

                                do {
                                    codePoints = new int[4];
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[0] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[1] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[2] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    var10002 = new StringBuilder("0x");
                                    ++index;
                                    var10002 = var10002.append(characterArray[index]);
                                    ++index;
                                    codePoints[3] = Integer.decode(var10002.append(characterArray[index]).toString());
                                    decodedString = decodedString.concat(decoderUTF32.decode(buffer(codePoints)).toString());
                                } while (characterArray[index + 1] != '\\');

                                index += 4;
                            }
                        }
                    }
                }
            }

            return decodedString;
        } catch (Exception e) {
            return "";
        }
    }

    private static ByteBuffer buffer(String string) {
        byte[] bytes = string.getBytes();
        bytes[0] = (byte) (bytes[0] + 128);
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.rewind();
        return buffer;
    }

    private static ByteBuffer buffer(int[] numbers) {
        ByteBuffer buffer = ByteBuffer.allocate(numbers.length);
        int[] var5 = numbers;
        int var4 = numbers.length;

        for (int var3 = 0; var3 < var4; ++var3) {
            int n = var5[var3];
            byte b = (byte) n;
            buffer.put(b);
        }

        buffer.rewind();
        return buffer;
    }
}
