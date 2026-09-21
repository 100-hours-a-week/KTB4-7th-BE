package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.junit.jupiter.api.Test;

class TossPosWorkbookFileTest {

    @Test
    void 토스_POS_엑셀_파일을_읽을_수_있다() throws Exception {
        try (InputStream inputStream = getClass()
            .getResourceAsStream("/sales/toss-pos/sample-normal.xlsx")) {
            assertThat(inputStream).isNotNull();

            int sheetCount = 0;
            try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
                XSSFReader.SheetIterator sheets = new XSSFReader(opcPackage, true)
                    .getSheetIterator();
                while (sheets.hasNext()) {
                    try (InputStream ignored = sheets.next()) {
                        sheetCount++;
                        System.out.println("시트 이름: " + sheets.getSheetName());
                    }
                }
            }
            assertThat(sheetCount).isGreaterThan(0);
        }
    }
}
