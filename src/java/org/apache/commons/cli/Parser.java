/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.cli;

import java.util.*;

/**
 * <p><code>Parser</code> creates {@link CommandLine}s.</p>
 *
 * @author John Keyes (john at integralsource.com)
 * @see Parser
 * @version $Revision: 551815 $
 */
public abstract class Parser implements CommandLineParser {

	/**
	 * <p>Subclasses must implement this method to reduce
	 * the <code>arguments</code> that have been passed to the parse
	 * method.</p>
	 *
	 * @param opts The Options to parse the arguments by.
	 * @param arguments The arguments that have to be flattened.
	 * @param stopAtNonOption specifies whether to stop
	 * flattening when a non option has been encountered
	 * @return a String array of the flattened arguments
	 */
	protected abstract String[] flatten(Options opts, String[] arguments,
			boolean stopAtNonOption);

	/**
	 * <p>Parses the specified <code>arguments</code>
	 * based on the specifed {@link Options}.</p>
	 *
	 * @param options the <code>Options</code>
	 * @param arguments the <code>arguments</code>
	 * @return the <code>CommandLine</code>
	 * @throws ParseException if an error occurs when parsing the
	 * arguments.
	 */
	public CommandLine parse(Options options, String[] arguments)
			throws ParseException {
		return parse(options, arguments, null, false);
	}

	/**
	 * Parse the arguments according to the specified options and
	 * properties.
	 *
	 * @param options the specified Options
	 * @param arguments the command line arguments
	 * @param properties command line option name-value pairs
	 * @return the list of atomic option and value tokens
	 *
	 * @throws ParseException if there are any problems encountered
	 * while parsing the command line tokens.
	 */
	public CommandLine parse(Options options, String[] arguments,
			Properties properties) throws ParseException {
		return parse(options, arguments, properties, false);
	}

	/**
	 * <p>Parses the specified <code>arguments</code>
	 * based on the specifed {@link Options}.</p>
	 *
	 * @param options the <code>Options</code>
	 * @param arguments the <code>arguments</code>
	 * @param stopAtNonOption specifies whether to stop
	 * interpreting the arguments when a non option has
	 * been encountered and to add them to the CommandLines
	 * args list.
	 *
	 * @return the <code>CommandLine</code>
	 * @throws ParseException if an error occurs when parsing the
	 * arguments.
	 */
	public CommandLine parse(Options options, String[] arguments,
			boolean stopAtNonOption) throws ParseException {
		return parse(options, arguments, null, stopAtNonOption);
	}

	/**
	 * Parse the arguments according to the specified options and
	 * properties.
	 *
	 * @param options the specified Options
	 * @param args the command line arguments
	 * @param properties command line option name-value pairs
	 * @param stopAtNonOption stop parsing the arguments when the first
	 * non option is encountered.
	 *
	 * @return the list of atomic option and value tokens
	 *
	 * @throws ParseException if there are any problems encountered
	 * while parsing the command line tokens.
	 */
	public CommandLine parse(Options options, String[] args,
			Properties properties, boolean stopAtNonOption)
					throws ParseException
	{
		// clear out the data in options in case it's been used before (CLI-71)
		for (Object opt : options.helpOptions())
			((Option)opt).clearValues();

		/** list of required options strings */
		List requiredOptions = options.getRequiredOptions();

		/** commandline instance */
		CommandLine cmd = new CommandLine();

		String[] tokens = args == null ? new String[0] : flatten(options, args, stopAtNonOption);
		int current = 0;

		// process each flattened token
		while (current < tokens.length) {
			String t = tokens[current++];

			// the value is the double-dash OR single dash and stopAtNonOption=true.
			if ((t+stopAtNonOption).matches("-true|--(true|false)"))
				break;

			// the value is a single dash
			if (t.equals("-")) {
				cmd.addArg(t);
				continue;
			}

			// the value is an option
			if (t.startsWith("-")) {

				if (!options.hasOption(t)) {
					if (stopAtNonOption) {
						cmd.addArg(t);
						break;
					}
					throw new UnrecognizedOptionException(
							"Unrecognized option: " + t);
				}

				// get the option represented by arg
				Option opt = options.getOption(t);

				// if the option is a required option remove the option from
				// the requiredOptions list
				if (opt.isRequired())
					requiredOptions.remove(opt.getKey());

				// if the option is in an OptionGroup make that option the
				// selected
				// option of the group
				OptionGroup group = options.getOptionGroup(opt);
				if (group != null) {
					if (group.isRequired())
						requiredOptions.remove(group);
					group.setSelected(opt);
				}


				// if the option takes an argument value
				if (opt.hasArg()) {
					// <processArgs>
					// loop until an option is found
					while (current < tokens.length) {
						String str = tokens[current];

						// found an Option, not an argument
						if (options.hasOption(str) && str.startsWith("-"))
							break;

						// found a value
						try {
							opt.addValueForProcessing(Util
									.stripLeadingAndTrailingQuotes(str));
						} catch (RuntimeException exp) {
							break;
						}
						++current;
					}

					if (opt.getValues() == null && !opt.hasOptionalArg())
						throw new MissingArgumentException(
								"Missing argument for option:" + opt.getKey());
					// </processArgs>
				}

				// set the option on the command line
				cmd.addOption(opt);
				// <ProcessOptions>
				continue;
			}

			// the value is an argument
			cmd.addArg(t);

			if (stopAtNonOption)
				break;
		}

		// eat the remaining tokens
		while (current < tokens.length)
			if (!tokens[current].equals("--"))
				cmd.addArg(tokens[current++]);

		// <processProperties>
		if (properties != null)
			for (String option : properties.stringPropertyNames()) {
				if (cmd.hasOption(option))
					continue;
				Option opt = options.getOption(option);

				// get the value from the properties instance
				String value = properties.getProperty(option);

				if (opt.hasArg()) {
					if (opt.getValues() == null || opt.getValues().length == 0)
						try {
							opt.addValueForProcessing(value);
						} catch (RuntimeException exp) {
							// if we cannot add the value don't worry about it
						}
				} else if (!value.toLowerCase().matches("yes|true|1"))
					// if the value is not yes, true or 1 then don't add the
					// option to the CommandLine
					break;

				cmd.addOption(opt);
			}
		// </processProperties>
		// <checkRequiredOptions>
		// if there are required options that have not been processsed
		if (requiredOptions.isEmpty())
			return cmd;

		String buff = requiredOptions.size() == 1 ? "Missing required option: "
				: "Missing required options: ";

		// loop through the required options
		for (Object option : requiredOptions)
			buff += option;

		throw new MissingOptionException(buff);
		// </checkRequiredOptions>
	}
}