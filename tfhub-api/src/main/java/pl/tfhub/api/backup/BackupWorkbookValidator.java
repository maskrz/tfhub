package pl.tfhub.api.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.poi.util.XMLHelper;

@Component
public class BackupWorkbookValidator {

	public void validate(Path file) {
		if (!Files.isRegularFile(file) || size(file) == 0) {
			throw new IllegalStateException("Generated XLSX file is missing or empty");
		}

		try (OPCPackage packageFile = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
			XSSFReader reader = new XSSFReader(packageFile);
			if (reader.getStylesTable() == null) {
				throw new IllegalStateException("Generated XLSX workbook has no styles table");
			}
			XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
			Set<String> sheetNames = new HashSet<>();
			while (sheets.hasNext()) {
				try (InputStream sheet = sheets.next()) {
					sheetNames.add(sheets.getSheetName());
					var parser = XMLHelper.newXMLReader();
					parser.setContentHandler(new DefaultHandler());
					parser.parse(new InputSource(sheet));
				}
			}
			if (!sheetNames.equals(Set.of("Wyniki", "Metadata"))) {
				throw new IllegalStateException("Generated XLSX workbook structure is invalid");
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Generated XLSX workbook is invalid", exception);
		}
	}

	private long size(Path file) {
		try {
			return Files.size(file);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect generated XLSX file", exception);
		}
	}
}
