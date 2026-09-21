package com.memme;

import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.BusinessVerificationRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.store.BusinessStatusClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MemmeApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SignupDraftRepository signupDraftRepository;

    @MockitoBean
    private BusinessVerificationRepository businessVerificationRepository;

    @MockitoBean
    private SalesDailySummaryRepository salesDailySummaryRepository;

    @MockitoBean
    private SalesOrderRepository salesOrderRepository;

    @MockitoBean
    private SalesOrderItemRepository salesOrderItemRepository;

    @MockitoBean
    private SalesUploadRepository salesUploadRepository;

    @MockitoBean
    private AnalysisRunRepository analysisRunRepository;

    @MockitoBean
    private StoreOwnershipRepository storeOwnershipRepository;

    @MockitoBean
    private BusinessStatusClient businessStatusClient;

    @Test
    void contextLoads() {
    }
}
