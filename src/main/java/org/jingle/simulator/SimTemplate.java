package org.jingle.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimTemplate {
	static class Token {
		private String text;
		private boolean isVariable = false;
		private String name;
		private int start;
		private int end;
		
		public Token(String text, String name, int start, int end) {
			this.text = text;
			this.name = name;
			this.start = start;
			this.end = end;
			this.isVariable = true;
		}

		public Token(String text, int start, int end) {
			this.text = text;
			this.start = start;
			this.end = end;
			this.isVariable = false;
		}

		public String getName() {
			return name;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

		public String getText() {
			return text;
		}

		public boolean isVariable() {
			return isVariable;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(text + "," + name + "," + start + "," + end);
			return sb.toString();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + end;
			result = prime * result + (isVariable ? 1231 : 1237);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + start;
			result = prime * result + ((text == null) ? 0 : text.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Token other = (Token) obj;
			if (end != other.end)
				return false;
			if (isVariable != other.isVariable)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (start != other.start)
				return false;
			if (text == null) {
				if (other.text != null)
					return false;
			} else if (!text.equals(other.text))
				return false;
			return true;
		}
	}
	
	private static final String PATTERN_STR = "\\{\\$(\\w+)\\}";
	private static final Pattern PATTERN = Pattern.compile(PATTERN_STR);
	private String templateContent;
	private List<List<Token>> allTokens = new ArrayList<>();
	
	public SimTemplate(String templateContent) throws IOException {
		this.templateContent = templateContent;
		init();
	}
	
	@Override
	public String toString() {
		return this.templateContent;
	}

	public SimTemplate(BufferedReader reader) throws IOException {
		String line = null;
		StringBuffer sb = new StringBuffer();
		while ((line = reader.readLine()) != null) {
			sb.append(line).append("\n");
		}
		this.templateContent = sb.toString();
		init();
	}
	
	public Map<String, Object> parse(String content) throws IOException {
		try (BufferedReader contentReader = new BufferedReader(new StringReader(content))) {
			return parse(contentReader);
		}

	}

	public Map<String, Object> parse(BufferedReader reader) throws IOException {
		int lineNum = 1;
		String contentLine = null;
		Map<String, Object> ret = new HashMap<>();
		while ((contentLine = reader.readLine()) != null) {
			List<Token> tokenList = (allTokens.size() >= lineNum ? allTokens.get(lineNum - 1) : null);
			if (tokenList != null) {
				Map<String, Object> lineResult = parseLine(contentLine, tokenList, 0);
				if (lineResult != null) {
					ret.putAll(lineResult);
				} else {
					ret = null;
					break;
//					throw new RuntimeException("template and actual content does not match @ line " + lineNum);
				}
			} else {
				ret = null;
				break;
//				throw new RuntimeException("template and actual content does not match @ line " + lineNum + ", no more template lines");
			}
			lineNum++;
		}
		if (allTokens.size() >= lineNum) {
			ret = null;
//			throw new RuntimeException("template and actual content does not match @ line " + lineNum + ", no more content lines");
		}
		return ret;
	}
	

	
	protected void init() throws IOException {
		try (BufferedReader reader = new BufferedReader(new StringReader(templateContent))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				allTokens.add(parseTemplateLine(line));
			}
		}
	}
	
	protected static List<Token> parseTemplateLine(String line) {
		List<Token> tokenList = new ArrayList<Token>();
		Matcher matcher = PATTERN.matcher(line);
		Token preToken = null;
		while (matcher.find()) {
			Token token = new Token(matcher.group(0), matcher.group(1), matcher.start(), matcher.end() - 1);
			int start = (preToken == null ? 0 : preToken.getEnd() + 1);
			int end = token.getStart() - 1;
			if (end >= start) {
				tokenList.add(new Token(line.substring(start, end + 1), start, end));
			} 
			tokenList.add(token);
			preToken = token;
		}
		
		int start = (preToken == null ? 0 : preToken.getEnd() + 1);
		int end = line.length() - 1;
		if (end >= start) {
			tokenList.add(new Token(line.substring(start, end + 1), start, end));
		} 
		return tokenList;
	}
	
	
	protected Map<String, Object> parseLine(String contentLine, List<Token> tokenList, int tokenIndex) {
		if (tokenIndex == tokenList.size() && contentLine.length() == 0)
			return new HashMap<>();
		if (tokenIndex == tokenList.size() || contentLine.length() == 0)
			return null;
		Token token = tokenList.get(tokenIndex);
		if (!token.isVariable()) {
			if (contentLine.indexOf(token.getText()) != 0)
				return null;
			return parseLine(contentLine.substring(token.getText().length()), tokenList, tokenIndex + 1);
		} else {
			for (int i = 0; i <= contentLine.length(); i++) {
				Map<String, Object> result = parseLine(contentLine.substring(i, contentLine.length()), tokenList, tokenIndex + 1);
				if (result != null) {
					result.put(token.getName(), contentLine.substring(0, i));
					return result;
				}
			}
			return null;
		}
	}
	
	public List<List<Token>> getAllTokens() {
		return this.allTokens;
	}
	
	public static void main(String[] args) throws IOException {
		SimTemplate template = new SimTemplate("Name of this good is {$good}. It's price is {$value}.\ndummy line\nName of that good is {$good2}. It's price is {$value2}");
		System.out.println(template.parse(""));
	}
}
