/*
The MIT License (MIT)

Copyright (c) 2015 Kimberly Horne

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package org.asmic.mta.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Usage: CreateDatabase <path to database> <path to data files>
 */
public class CreateDatabase {

	static class CSVFile {

		private String path;
		private String file;
		private Transformer[] transformers;

		public CSVFile(String path, String file, Transformer... transformers) {
			this.path = path;
			this.file = file;
			this.transformers = transformers;
		}

		public String getFullPath() {
			return path + "/" + file + ".txt";
		}

		public String getFilename() {
			return file;
		}

		public Transformer[] getTransformers() {
			return transformers;
		}
	}

	private interface Transformer {
		String transform(String value);
	}

	private enum Transformations implements Transformer {
		no {
			@Override
			public String transform(String value) {
				if (value.length() == 0) {
					return "0";
				}
				return value;
			}
		},

		quote {
			@Override
			public String transform(String value) {
				return "'" + value.replace("'", "''") + "'";
			}
		},
		magical_time {
			@Override
			public String transform(String value) {
				int timeFromMidnightInSeconds = 0;
				int multiplier = 1;
				String[] parts = value.split(":");
				if (parts.length != 3) {
					System.err.println("Invalid date located : " + value);
					System.exit(2);
				}
				for (int i = 2; i >= 0; i--) {
					timeFromMidnightInSeconds += (Integer.parseInt(parts[i]) * multiplier);
					multiplier *= 60;
				}
				return Integer.toString(timeFromMidnightInSeconds);
			}
		};
	}

	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err
					.println("Usage: CreateDatabase <path to database> <path to data files>");
			System.exit(1);
		}

		Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:"
				+ args[0], "SA", "");
		createTables(connection);
		final String dataPath = args[1];

		populateTables(connection, new CSVFile(dataPath, "routes",
				Transformations.quote, Transformations.quote,
				Transformations.quote, Transformations.quote,
				Transformations.quote, Transformations.no,
				Transformations.quote, Transformations.quote,
				Transformations.quote), new CSVFile(dataPath, "stop_times",
				Transformations.quote, Transformations.magical_time,
				Transformations.magical_time, Transformations.quote,
				Transformations.no, Transformations.quote, Transformations.no,
				Transformations.no, Transformations.no), new CSVFile(dataPath,
				"stops", Transformations.quote, Transformations.quote,
				Transformations.quote, Transformations.quote,
				Transformations.no, Transformations.no, Transformations.no,
				Transformations.no, Transformations.no, Transformations.quote),
				new CSVFile(dataPath, "trips", Transformations.quote,
						Transformations.quote, Transformations.quote,
						Transformations.quote, Transformations.no,
						Transformations.no, Transformations.quote),
				new CSVFile(dataPath, "calendar", Transformations.quote,
						Transformations.no, Transformations.no,
						Transformations.no, Transformations.no,
						Transformations.no, Transformations.no,
						Transformations.no, Transformations.quote,
						Transformations.quote));
		connection.close();
	}

	private static void populateTables(Connection connection, CSVFile... files)
			throws IOException, SQLException {
		for (CSVFile file : files) {
			populateTable(connection, file);
		}
	}

	private static void populateTable(Connection connection, CSVFile file)
			throws IOException, SQLException {
		System.out.println("Populating " + file.getFullPath());
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(new File(file.getFullPath()))));
		Transformer[] transformers = file.getTransformers();
		String line = reader.readLine();
		int columnCount = line.split(",").length;

		int rowCount = 0;
		int batchCount = 0;
		Statement statement = connection.createStatement();
		String[] split;
		while ((line = reader.readLine()) != null) {
			split = split(line);
			StringBuffer buffer = new StringBuffer("INSERT INTO mta.");
			buffer.append(file.getFilename());
			buffer.append(" VALUES (");
			for (int i = 0; i < columnCount; i++) {
				if (i < split.length) {
					buffer.append(transformers[i].transform(split[i]));
					if (i <= split.length - 1) {
						buffer.append(',');
					}
				} else {
					buffer.append(transformers[i].transform(""));
				}
			}
			buffer.append(")");
//			System.out.println(buffer.toString());
			statement.addBatch(buffer.toString());
			batchCount++;
			rowCount++;
			if (batchCount >= 500) {
				processBatch(statement);
				System.out.println("Wrote " + batchCount + " records ("
						+ rowCount + " total)");
				batchCount = 0;
				statement = connection.createStatement();
			}
		}
		reader.close();

		if (batchCount > 0) {
			processBatch(statement);
		}

		System.out.println("Row count for " + file.getFilename() + ": " + rowCount);

		statement = connection.createStatement();
		ResultSet set = statement.executeQuery("SELECT COUNT(*) FROM mta."
				+ file.getFilename());
		if (set.next()) {
			System.out.println("Read count : " + set.getInt(1));
		} else {
			System.err.println("Problem obtaining read count.");
		}
		statement.close();
	}

	private static void processBatch(Statement statement) throws SQLException {
		int[] results = statement.executeBatch();
		statement.close();
		for (int i = 0; i < results.length; i++) {
			if (results[i] == Statement.EXECUTE_FAILED) {
				System.err.println("Problem executing insert.");
				System.exit(3);
			}
		}
	}

	private static String[] split(String line) {
		ArrayList<String> split = new ArrayList<>();
		StringBuffer current = new StringBuffer();
		boolean inQuoted = false;
		for (int i = 0; i < line.length(); i++) {
			char currentCharacter = line.charAt(i);
			if (!inQuoted && currentCharacter == ',') {
				split.add(current.toString());
				current.setLength(0);
			} else {
				if (currentCharacter == '\"') {
					inQuoted = !inQuoted;
				}
				current.append(currentCharacter);
			}
		}
		return split.toArray(new String[split.size()]);
	}

	private static void createTables(Connection connection) throws IOException,
			SQLException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				CreateDatabase.class.getResourceAsStream("createdatabase.sql")));

		String line = null;
		String currentStatement = "";
		while ((line = reader.readLine()) != null) {
			currentStatement += line;
			if (currentStatement.endsWith(";")) {
				executeLine(connection, currentStatement);
				currentStatement = "";
			}
		}
		if (currentStatement.endsWith(";")) {
			executeLine(connection, currentStatement);
		}
	}

	private static void executeLine(Connection connection,
			String currentStatement) throws SQLException {
		System.out.println(currentStatement);
		PreparedStatement statement = connection
				.prepareStatement(currentStatement);
		statement.executeUpdate();
		statement.close();
	}
}
