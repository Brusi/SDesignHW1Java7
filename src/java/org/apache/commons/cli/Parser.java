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

	/** commandline instance */
	private CommandLine cmd;

	/** current Options */
	private Options options;

	/** list of required options strings */
	private List requiredOptions;

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
			throws ParseException
	{
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
			Properties properties)
					throws ParseException
	{
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
			boolean stopAtNonOption)
					throws ParseException
	{
		return parse(options, arguments, null, stopAtNonOption);
	}

	/**
	 * Parse the arguments according to the specified options and
	 * properties.
	 *
	 * @param options the specified Options
	 * @param arguments the command line arguments
	 * @param properties command line option name-value pairs
	 * @param stopAtNonOption stop parsing the arguments when the first
	 * non option is encountered.
	 *
	 * @return the list of atomic option and value tokens
	 *
	 * @throws ParseException if there are any problems encountered
	 * while parsing the command line tokens.
	 */
	public CommandLine parse(Options opts, String[] arguments,
			Properties properties, boolean stopAtNonOption)
					throws ParseException
	{
		// initialise members
		options = opts;

		// clear out the data in options in case it's been used before (CLI-71)
		for (Object opt : options.helpOptions())
			((Option)opt).clearValues();

		requiredOptions = options.getRequiredOptions(); // TODO eliminate field
		cmd = new CommandLine();

		ListIterator<String> iterator = Arrays.asList(flatten(options,
				arguments != null ? arguments : new String[0],
						stopAtNonOption)).listIterator();

		// process each flattened token
		while (iterator.hasNext()) {
			String t = iterator.next();

			// the value is the double-dash OR single dash and stopAtNonOption=true.
			if ((t+stopAtNonOption).matches("--(true|false)|-true"))
				break;

			// the value is a single dash
			if ("-".equals(t)) {
				cmd.addArg(t);
				continue;
			}

			// the value is an option
			if (t.startsWith("-")) {
				if (stopAtNonOption && !options.hasOption(t)) {
					cmd.addArg(t);
					break;
				}
				processOption(t, iterator);
				continue;
			}

			// the value is an argument
			cmd.addArg(t);

			if (stopAtNonOption)
				break;
		}

		// eat the remaining tokens
		while (iterator.hasNext()) {
			String t = iterator.next();

			// ensure only one double-dash is added
			if (!"--".equals(t))
				cmd.addArg(t);
		}

		processProperties(properties);
		checkRequiredOptions();

		return cmd;
	}

	/**
	 * <p>Sets the values of Options using the values in
	 * <code>properties</code>.</p>
	 *
	 * @param properties The value properties to be processed.
	 */
	private void processProperties(Properties properties) {
		if (properties == null)
			return;

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
	}

	/**
	 * <p>Throws a {@link MissingOptionException} if all of the
	 * required options are no present.</p>
	 *
	 * @throws MissingOptionException if any of the required Options
	 * are not present.
	 */
	private void checkRequiredOptions()
			throws MissingOptionException
	{
		// if there are required options that have not been
		// processsed
		if (requiredOptions.size() <= 0) return;

		Iterator iter = requiredOptions.iterator();
		String buff = requiredOptions.size() == 1 ? "Missing required option: " : "Missing required options: ";

		// loop through the required options
		for (Object option : requiredOptions)
			buff += option;

		throw new MissingOptionException(buff.toString());
	}

	/**
	 * <p>Process the argument values for the specified Option
	 * <code>opt</code> using the values retrieved from the
	 * specified iterator <code>iter</code>.
	 *
	 * @param opt The current Option
	 * @param iter The iterator over the flattened command line
	 * Options.
	 *
	 * @throws ParseException if an argument value is required
	 * and it is has not been found.
	 */
	public void processArgs(Option opt, ListIterator iter)
			throws ParseException
	{
		// loop until an option is found
		while (iter.hasNext())
		{
			String str = (String) iter.next();

			// found an Option, not an argument
			//			if (options.hasOption(str) && str.startsWith("-"))
			//			{
			//				iter.previous();
			//				break;
			//			}

			// found a value
			try
			{
				if (options.hasOption(str) && str.startsWith("-"))
					throw new RuntimeException();
				opt.addValueForProcessing( Util.stripLeadingAndTrailingQuotes(str) );
				// TODO: is that ok?
			}
			catch (RuntimeException exp)
			{
				iter.previous();
				break;
			}
		}

		if (opt.getValues() == null && !opt.hasOptionalArg())
			throw new MissingArgumentException("Missing argument for option:"
					+ opt.getKey());
	}

	/**
	 * <p>Process the Option specified by <code>arg</code>
	 * using the values retrieved from the specfied iterator
	 * <code>iter</code>.
	 *
	 * @param arg The String value representing an Option
	 * @param iter The iterator over the flattened command
	 * line arguments.
	 *
	 * @throws ParseException if <code>arg</code> does not
	 * represent an Option
	 */
	private void processOption(String arg, ListIterator iter)
			throws ParseException
	{
		// if there is no option throw an UnrecognisedOptionException
		if (!options.hasOption(arg))
			throw new UnrecognizedOptionException("Unrecognized option: " + arg);

		// get the option represented by arg
		Option opt = options.getOption(arg);

		// if the option is a required option remove the option from
		// the requiredOptions list
		if (opt.isRequired())
			requiredOptions.remove(opt.getKey());

		// if the option is in an OptionGroup make that option the selected
		// option of the group
		OptionGroup group = options.getOptionGroup(opt);
		if (group  != null) {
			if (group.isRequired())
				requiredOptions.remove(group);
			group.setSelected(opt);
		}

		// if the option takes an argument value
		if (opt.hasArg())
			processArgs(opt, iter);

		// set the option on the command line
		cmd.addOption(opt);
	}
}
