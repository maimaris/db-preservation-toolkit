package com.databasepreservation.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.databasepreservation.model.structure.ColumnStructure;
import com.databasepreservation.model.structure.TableStructure;
import com.databasepreservation.model.structure.type.Type;

/**
 * Reports warnings regarding data losses and transformations during the
 * database conversion.
 * 
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class Reporter {
  // //////////////////////////////////////////////////
  // constants
  public static final String MESSAGE_FILTERED_PASSWORD = "<filtered>";
  public static final String CODE_DELIMITER = "`";

  private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);
  private static final String[] NUMBER_SUFFIXES = new String[] {"th", "st", "nd", "rd", "th", "th", "th", "th", "th",
    "th"};

  private static final String MESSAGE_LINE_PREFIX_ALL = "- ";
  private static final String MESSAGE_LINE_DEFAULT_PREFIX = "Information: ";
  private static final String EMPTY_MESSAGE_LINE = "";
  private static final String NEWLINE = System.getProperty("line.separator", "\n");

  private static int countModuleInfoReported = 0;

  private static long conversionProblemsCounter = 0;

  // //////////////////////////////////////////////////
  // instance
  private Path outputfile;
  private BufferedWriter writer;

  /**
   * initializes message prefixes
   */
  static {
    getInstance();
  }

  private Reporter() {
    String filename_prefix = "dbptk-report-";
    String filename_timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
    String filename_suffix = ".txt";
    outputfile = Paths.get(".").toAbsolutePath().resolve(filename_prefix + filename_timestamp + filename_suffix);
    try {
      outputfile = Files.createFile(outputfile);
    } catch (IOException e) {
      LOGGER.warn("Could not create report file in current working directory. Attempting to use a temporary file", e);
      try {
        outputfile = Files.createTempFile(filename_prefix, filename_suffix);
      } catch (IOException e1) {
        LOGGER.error("Could not create report temporary file. Reporting will not function.", e);
      }
    }

    if (outputfile != null) {
      try {
        writer = Files.newBufferedWriter(outputfile, StandardCharsets.UTF_8);
      } catch (IOException e) {
        LOGGER.error("Could not get a writer for the report file.", e);
      }
    }
  }

  private void writeLine(String line) {
    if (outputfile == null || writer == null) {
      LOGGER.info(line);
    } else {
      try {
        writer.write(line);
        writer.newLine();
      } catch (IOException e) {
        if (e.getMessage().equals("Stream closed")) {
          writer = null;
        }
        LOGGER.info(line);
      }
    }
  }

  // //////////////////////////////////////////////////
  // static
  private static Reporter reporterInstance;

  private static void report(StringBuilder message) {
    report(message, MESSAGE_LINE_PREFIX_ALL);
  }

  private static void report(StringBuilder message, String prefix) {
    Reporter reporter = getInstance();
    if (prefix != null) {
      message.insert(0, prefix);
    }
    reporter.writeLine(message.toString());
  }

  private static Reporter getInstance() {
    if (reporterInstance == null) {
      reporterInstance = new Reporter();

      reporterInstance.writeLine("Database Preservation Toolkit -- Conversion Report");
      reporterInstance.writeLine("==================================================");
      reporterInstance.writeLine(EMPTY_MESSAGE_LINE);
    }
    return reporterInstance;
  }

  /**
   * Utility to get the ordinal suffix for a number
   */
  private static String ordinal(long i) {
    switch ((int) i % 100) {
      case 11:
      case 12:
      case 13:
        return NUMBER_SUFFIXES[0];
      default:
        return NUMBER_SUFFIXES[(int) i % 10];

    }
  }

  /**
   * Adds the code string delimited by the CODE_DELIMITER to the string builder
   */
  private static StringBuilder appendAsCode(StringBuilder sb, String code) {
    sb.append(CODE_DELIMITER).append(code).append(CODE_DELIMITER);
    return sb;
  }

  private static void moduleParameters(String moduleName, String importOrExport, String... parameters) {
    StringBuilder message;

    if (countModuleInfoReported == 0) {
      message = new StringBuilder("## Parameters").append(NEWLINE);
    } else {
      message = new StringBuilder();
    }

    message.append("**").append(importOrExport).append(" module:** ").append(moduleName);

    for (int i = 0; i < parameters.length; i += 2) {
      message.append(NEWLINE).append("- ").append(parameters[i]).append(" = ").append(parameters[i + 1]);
    }

    countModuleInfoReported++;

    LOGGER.debug("moduleParameters, module: " + moduleName + " with parameters " + message);
    if (countModuleInfoReported == 2) {
      message.append(NEWLINE).append(NEWLINE).append("Date: ")
        .append(new SimpleDateFormat("yyyy-MM-dd").format(new Date())).append(NEWLINE).append(NEWLINE)
        .append("## Details");
    } else {
      message.append(NEWLINE);
    }
    report(message, null);
  }

  // //////////////////////////////////////////////////
  // static public
  public static void finish() {
    Reporter reporter = getInstance();
    try {
      if (reporter.writer != null) {
        reporter.writer.close();
        if (conversionProblemsCounter == 0) {
          Files.deleteIfExists(reporter.outputfile);
        }
      }
    } catch (IOException e) {
      LOGGER.debug("Unable to close Reporter file", e);
    }

    if (conversionProblemsCounter != 0) {
      if (reporter.writer != null) {
        LOGGER.info("A report was generated with a listing of information that was modified during the conversion.");
        LOGGER.info("The report file is located at " + reporter.outputfile.normalize().toAbsolutePath().toString());
      } else {
        LOGGER
          .info("A report with a listing of information that was modified during the conversion could not be generated, please submit a bug report to help us fix this.");
      }
    }
  }

  public static void cellProcessingUsedNull(TableStructure table, ColumnStructure column, long rowIndex,
    Throwable exception) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder("Problem processing cell value and NULL was used instead, ");

    if (table != null && column != null) {
      message.append("in table ");
      appendAsCode(message, table.getId()).append(", in column ");
      appendAsCode(message, column.getName()).append(", ");
    } else if (column != null) {
      message.append("in column ");
      appendAsCode(message, column.getName()).append(", ");
    } else {
      message.append("in an unidentified table and column, ");
    }
    message.append("in the ").append(rowIndex).append(ordinal(rowIndex)).append(" row");

    report(message);
    LOGGER.debug("cellProcessingUsedNull, message: " + message, exception);
  }

  public static void rowProcessingUsedNull(TableStructure table, long rowIndex, Throwable exception) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder(
      "Problem processing row values and NULL was used instead for all cells, in table ");
    appendAsCode(message, table.getId()).append(" in the ").append(rowIndex).append(ordinal(rowIndex)).append(" row");

    report(message);
    LOGGER.debug("cellProcessingUsedNull, message: " + message, exception);
  }

  public static void notYetSupported(String feature, String module) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder(MESSAGE_LINE_DEFAULT_PREFIX).append(feature)
      .append(" is not yet supported for ").append(module).append(". But support may be added in the future");

    report(message);
    LOGGER.debug("notYetSupported, message: " + message);
  }

  public static void dataTypeChangedOnImport(String invokerNameForDebug, String schemaName, String tableName,
    String columnName, Type type) {
    conversionProblemsCounter++;

    String original = null;
    if (type != null && StringUtils.isNotBlank(type.getOriginalTypeName())) {
      original = type.getOriginalTypeName().toUpperCase(Locale.ENGLISH);
    }

    String sql99 = type.getSql99TypeName().toUpperCase(Locale.ENGLISH);
    String sql2008 = type.getSql2008TypeName().toUpperCase(Locale.ENGLISH);

    StringBuilder message = new StringBuilder("Type conversion in import module: in ");
    appendAsCode(message, schemaName + "." + tableName + "." + columnName);
    message.append(" (format: schema.table.column) has ");

    if (original == null) {
      message.append("an unidentified original type");
    } else {
      message.append("original type ");
      appendAsCode(message, original);
    }

    message.append(" and was converted to the standard type ");

    if (sql99.equals(sql2008)) {
      appendAsCode(message, sql99);
    } else {
      appendAsCode(message, sql99).append(" and ");
      appendAsCode(message, sql2008).append(" (SQL99 and SQL2008 standard)");
    }

    if (original != null) {
      report(message);
    }
    LOGGER.debug("dataTypeChangedOnImport, invoker: " + invokerNameForDebug + "; message: " + message + "; and type: "
      + type);
  }

  public static void dataTypeChangedOnExport(String invokerNameForDebug, ColumnStructure column, String typeSQL) {
    conversionProblemsCounter++;

    String original = column.getType().getOriginalTypeName().toUpperCase(Locale.ENGLISH);
    String sql99 = column.getType().getSql99TypeName().toUpperCase(Locale.ENGLISH);
    String sql2008 = column.getType().getSql2008TypeName().toUpperCase(Locale.ENGLISH);

    StringBuilder message = new StringBuilder("Type conversion in export module: in ");
    appendAsCode(message, column.getId()).append(" (format: schema.table.column) has original type ");
    appendAsCode(message, original).append(" and standard type ");

    if (sql99.equals(sql2008)) {
      appendAsCode(message, sql99);
    } else {
      appendAsCode(message, sql99).append(" and ");
      appendAsCode(message, sql2008).append(" (SQL99 and SQL2008 standard)");
    }

    message.append(", will be created as ");
    appendAsCode(message, typeSQL).append(" in the target database");

    report(message);
    LOGGER.debug("dataTypeChangedOnExport, invoker: " + invokerNameForDebug + "; message: " + message
      + "; and column: " + column);
  }

  public static void exportModuleParameters(String moduleName, String... parameters) {
    moduleParameters(moduleName, "Export", parameters);
  }

  public static void importModuleParameters(String moduleName, String... parameters) {
    moduleParameters(moduleName, "Import", parameters);
  }

  public static void customMessage(String invokerNameForDebug, String customMessage, String prefix) {
    StringBuilder message = new StringBuilder();
    if (prefix != null) {
      message.append(prefix).append(": ");
    } else {
      message.append(MESSAGE_LINE_DEFAULT_PREFIX);
    }
    message.append(customMessage);
    report(message);
    LOGGER.debug("customMessage, invoker: " + invokerNameForDebug + "; message: " + message.toString());
  }

  public static void customMessage(String invokerNameForDebug, String customMessage) {
    customMessage(invokerNameForDebug, customMessage, null);
  }

  public static void ignored(String whatWasIgnored, String whyItWasIgnored) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder(MESSAGE_LINE_DEFAULT_PREFIX);
    appendAsCode(message, whatWasIgnored).append(" was ignored because ").append(whyItWasIgnored);

    report(message);
    LOGGER.debug("something was ignored, message: " + message);
  }

  public static void failed(String whatFailed, String whyItFailed) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder(MESSAGE_LINE_DEFAULT_PREFIX).append(whatFailed)
      .append(" failed because ").append(whyItFailed);

    report(message);
    LOGGER.debug("something failed, message: " + message);
  }

  public static void valueChanged(String originalValue, String newValue, String reason, String location) {
    conversionProblemsCounter++;
    StringBuilder message = new StringBuilder("Warning: ");
    appendAsCode(message, originalValue).append(" changed to ");
    appendAsCode(message, newValue).append(" because ").append(reason).append(" in ").append(location);

    report(message);
    LOGGER.debug("something failed, message: " + message);
  }
}
