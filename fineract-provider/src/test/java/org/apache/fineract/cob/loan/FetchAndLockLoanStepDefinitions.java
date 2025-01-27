/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.cob.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cucumber.java8.En;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.cob.common.CustomJobParameterResolver;
import org.apache.fineract.cob.data.LoanCOBParameter;
import org.apache.fineract.cob.domain.LoanAccountLock;
import org.apache.fineract.cob.domain.LoanAccountLockRepository;
import org.apache.fineract.cob.domain.LockOwner;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.repeat.RepeatStatus;

public class FetchAndLockLoanStepDefinitions implements En {

    private final LoanAccountLockRepository loanAccountLockRepository = mock(LoanAccountLockRepository.class);
    private final FineractProperties fineractProperties = mock(FineractProperties.class);
    private final FineractProperties.FineractQueryProperties fineractQueryProperties = mock(
            FineractProperties.FineractQueryProperties.class);
    StepContribution contribution;
    ArgumentCaptor<LocalDate> dateValueCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LoanCOBParameter> loanCOBParameterValueCaptor = ArgumentCaptor.forClass(LoanCOBParameter.class);

    ArgumentCaptor<Boolean> isCatchUpParameterCaptor = ArgumentCaptor.forClass(Boolean.class);
    private LockLoanTasklet lockLoanTasklet;
    private String action;
    private RepeatStatus result;
    private final LoanLockingService loanLockingService = mock(LoanLockingService.class);

    private final CustomJobParameterResolver customJobParameterResolver = mock(CustomJobParameterResolver.class);

    public FetchAndLockLoanStepDefinitions() {
        Given("/^The FetchAndLockLoanTasklet.execute method with action (.*)$/", (String action) -> {
            ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
            HashMap<BusinessDateType, LocalDate> businessDateMap = new HashMap<>();
            businessDateMap.put(BusinessDateType.COB_DATE, LocalDate.now(ZoneId.systemDefault()));
            ThreadLocalContextUtil.setBusinessDates(businessDateMap);
            this.action = action;

            JobExecution jobExecution = new JobExecution(1L);
            StepExecution stepExecution = new StepExecution("step", jobExecution);
            contribution = new StepContribution(stepExecution);
            contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.BUSINESS_DATE_PARAMETER_NAME,
                    LocalDate.now(ZoneId.systemDefault()).toString());
            lenient().when(customJobParameterResolver.getCustomJobParameterSet(Mockito.any())).thenReturn(Optional.empty());
            if ("empty loanIds".equals(action)) {
                contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.LOAN_COB_PARAMETER,
                        new LoanCOBParameter(0L, 0L));
            } else if ("good".equals(action)) {
                contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.LOAN_COB_PARAMETER,
                        new LoanCOBParameter(1L, 3L));
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanAccountLockRepository.findAllByLoanIdIn(Mockito.anyList())).thenReturn(Collections.emptyList());
            } else if ("soft lock".equals(action)) {
                contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.LOAN_COB_PARAMETER,
                        new LoanCOBParameter(1L, 3L));
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanAccountLockRepository.findAllByLoanIdIn(Mockito.anyList())).thenReturn(
                        List.of(new LoanAccountLock(1L, LockOwner.LOAN_COB_PARTITIONING, LocalDate.now(ZoneId.systemDefault()))));
            } else if ("inline cob".equals(action)) {
                contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.LOAN_COB_PARAMETER,
                        new LoanCOBParameter(1L, 3L));
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanAccountLockRepository.findAllByLoanIdIn(Mockito.anyList())).thenReturn(
                        List.of(new LoanAccountLock(2L, LockOwner.LOAN_INLINE_COB_PROCESSING, LocalDate.now(ZoneId.systemDefault()))));
            } else if ("chunk processing".equals(action)) {
                contribution.getStepExecution().getJobExecution().getExecutionContext().put(LoanCOBConstant.LOAN_COB_PARAMETER,
                        new LoanCOBParameter(1L, 3L));
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanAccountLockRepository.findAllByLoanIdIn(Mockito.anyList())).thenReturn(
                        List.of(new LoanAccountLock(3L, LockOwner.LOAN_COB_CHUNK_PROCESSING, LocalDate.now(ZoneId.systemDefault()))));
            }

            lockLoanTasklet = new LockLoanTasklet(loanLockingService, customJobParameterResolver);
        });

        When("FetchAndLockLoanTasklet.execute method executed", () -> {
            result = lockLoanTasklet.execute(contribution, null);
        });

        Then("FetchAndLockLoanTasklet.execute result should match", () -> {
            if ("empty steps".equals(action)) {
                assertEquals(RepeatStatus.FINISHED, result);
            } else if ("good".equals(action)) {
                verify(loanLockingService).applySoftLock(dateValueCaptor.capture(), loanCOBParameterValueCaptor.capture(),
                        isCatchUpParameterCaptor.capture());
                assertEquals(LocalDate.now(ZoneId.systemDefault()).minusDays(1), dateValueCaptor.getValue());
                assertEquals(1L, loanCOBParameterValueCaptor.getValue().getMinLoanId());
                assertEquals(3L, loanCOBParameterValueCaptor.getValue().getMaxLoanId());
                assertEquals(RepeatStatus.FINISHED, result);
                assertEquals(false, isCatchUpParameterCaptor.getValue());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) contribution.getStepExecution().getJobExecution()
                        .getExecutionContext().get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(2, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(1L, loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMaxLoanId());
            } else if ("soft lock".equals(action)) {
                verify(loanLockingService).applySoftLock(dateValueCaptor.capture(), loanCOBParameterValueCaptor.capture(),
                        isCatchUpParameterCaptor.capture());
                assertEquals(LocalDate.now(ZoneId.systemDefault()).minusDays(1), dateValueCaptor.getValue());
                assertEquals(1L, loanCOBParameterValueCaptor.getValue().getMinLoanId());
                assertEquals(3L, loanCOBParameterValueCaptor.getValue().getMaxLoanId());
                assertEquals(RepeatStatus.FINISHED, result);
                assertEquals(false, isCatchUpParameterCaptor.getValue());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) contribution.getStepExecution().getJobExecution()
                        .getExecutionContext().get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(2, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(1L, loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMaxLoanId());
            } else if ("inline cob".equals(action)) {
                verify(loanLockingService).applySoftLock(dateValueCaptor.capture(), loanCOBParameterValueCaptor.capture(),
                        isCatchUpParameterCaptor.capture());
                assertEquals(LocalDate.now(ZoneId.systemDefault()).minusDays(1), dateValueCaptor.getValue());
                assertEquals(1L, loanCOBParameterValueCaptor.getValue().getMinLoanId());
                assertEquals(3L, loanCOBParameterValueCaptor.getValue().getMaxLoanId());
                assertEquals(RepeatStatus.FINISHED, result);
                assertEquals(false, isCatchUpParameterCaptor.getValue());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) contribution.getStepExecution().getJobExecution()
                        .getExecutionContext().get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(2, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(1L, loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMaxLoanId());
            } else if ("chunk processing".equals(action)) {
                verify(loanLockingService).applySoftLock(dateValueCaptor.capture(), loanCOBParameterValueCaptor.capture(),
                        isCatchUpParameterCaptor.capture());
                assertEquals(LocalDate.now(ZoneId.systemDefault()).minusDays(1), dateValueCaptor.getValue());
                assertEquals(1L, loanCOBParameterValueCaptor.getValue().getMinLoanId());
                assertEquals(3L, loanCOBParameterValueCaptor.getValue().getMaxLoanId());
                assertEquals(RepeatStatus.FINISHED, result);
                assertEquals(false, isCatchUpParameterCaptor.getValue());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) contribution.getStepExecution().getJobExecution()
                        .getExecutionContext().get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(2, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(1L, loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMaxLoanId());
            }
        });
    }
}
