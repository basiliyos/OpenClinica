/*
 * OpenClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: http://www.openclinica.org/license
 * copyright 2003-2007 Akaza Research
 */

package org.akaza.openclinica.control.managestudy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import core.org.akaza.openclinica.bean.admin.AuditBean;
import core.org.akaza.openclinica.bean.admin.CRFBean;
import core.org.akaza.openclinica.bean.admin.DeletedEventCRFBean;
import core.org.akaza.openclinica.bean.core.Role;
import core.org.akaza.openclinica.bean.core.Status;
import core.org.akaza.openclinica.bean.core.Utils;
import core.org.akaza.openclinica.bean.managestudy.EventDefinitionCRFBean;
import core.org.akaza.openclinica.bean.managestudy.StudyEventBean;
import core.org.akaza.openclinica.bean.managestudy.StudyEventDefinitionBean;
import core.org.akaza.openclinica.bean.managestudy.StudySubjectBean;
import core.org.akaza.openclinica.bean.submit.EventCRFBean;
import core.org.akaza.openclinica.bean.submit.FormLayoutBean;
import core.org.akaza.openclinica.bean.submit.ItemDataBean;
import core.org.akaza.openclinica.bean.submit.SubjectBean;
import core.org.akaza.openclinica.dao.hibernate.StudyDao;
import core.org.akaza.openclinica.domain.datamap.Study;
import liquibase.util.StringUtils;
import org.akaza.openclinica.control.SpringServletAccess;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.control.form.FormProcessor;
import org.akaza.openclinica.control.submit.SubmitDataServlet;
import core.org.akaza.openclinica.dao.admin.AuditDAO;
import core.org.akaza.openclinica.dao.admin.CRFDAO;
import core.org.akaza.openclinica.dao.hibernate.EventDefinitionCrfPermissionTagDao;
import core.org.akaza.openclinica.dao.managestudy.EventDefinitionCRFDAO;
import core.org.akaza.openclinica.dao.managestudy.StudyEventDAO;
import core.org.akaza.openclinica.dao.managestudy.StudyEventDefinitionDAO;
import core.org.akaza.openclinica.dao.managestudy.StudySubjectDAO;
import core.org.akaza.openclinica.dao.submit.EventCRFDAO;
import core.org.akaza.openclinica.dao.submit.FormLayoutDAO;
import core.org.akaza.openclinica.dao.submit.ItemDataDAO;
import core.org.akaza.openclinica.dao.submit.SubjectDAO;
import core.org.akaza.openclinica.domain.datamap.EventDefinitionCrfPermissionTag;
import core.org.akaza.openclinica.domain.datamap.Study;
import core.org.akaza.openclinica.i18n.util.ResourceBundleProvider;
import core.org.akaza.openclinica.web.InsufficientPermissionException;
import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.Colour;
import jxl.format.UnderlineStyle;
import jxl.write.*;
import org.akaza.openclinica.control.SpringServletAccess;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.control.form.FormProcessor;
import org.akaza.openclinica.control.submit.SubmitDataServlet;
import org.akaza.openclinica.domain.enumsupport.EventCrfWorkflowStatusEnum;
import org.akaza.openclinica.domain.enumsupport.StudyEventWorkflowStatusEnum;
import org.akaza.openclinica.view.Page;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jsampson
 * @author akung
 */

@SuppressWarnings("serial")
public class ExportExcelStudySubjectAuditLogServlet extends SecureController {

    private StudyEventDAO studyEventDAO;
    private EventCRFDAO eventCRFDAO;
    /**
     * Checks whether the user has the right permission to proceed function
     */
    @Override
    public void mayProceed() throws InsufficientPermissionException {

        if (ub.isSysAdmin()) {
            return;
        }

        if (SubmitDataServlet.mayViewData(ub, currentRole)) {
            return;
        }

        addPageMessage(respage.getString("no_have_correct_privilege_current_study") + " " + respage.getString("change_study_contact_sysadmin"));
        throw new InsufficientPermissionException(Page.LIST_STUDY_SUBJECTS, resexception.getString("not_study_director"), "1");

    }

    @Override
    public void processRequest() throws Exception {
        studyEventDAO = (StudyEventDAO) SpringServletAccess.getApplicationContext(context).getBean("studyEventJDBCDao");
        eventCRFDAO = (EventCRFDAO) SpringServletAccess.getApplicationContext(context).getBean("eventCRFJDBCDao");
        EventDefinitionCrfPermissionTagDao eventDefinitionCrfPermissionTagDao = (EventDefinitionCrfPermissionTagDao) SpringServletAccess.getApplicationContext(context).getBean("eventDefinitionCrfPermissionTagDao");
        StudySubjectDAO subdao = new StudySubjectDAO(sm.getDataSource());
        SubjectDAO sdao = new SubjectDAO(sm.getDataSource());
        AuditDAO adao = new AuditDAO(sm.getDataSource());

        StudyEventDefinitionDAO seddao = new StudyEventDefinitionDAO(sm.getDataSource());
        EventDefinitionCRFDAO edcdao = new EventDefinitionCRFDAO(sm.getDataSource());
        CRFDAO cdao = new CRFDAO(sm.getDataSource());
        FormLayoutDAO fldao = new FormLayoutDAO(sm.getDataSource());
        StudySubjectBean studySubject = null;
        SubjectBean subject = null;
        ArrayList events = null;
        ArrayList studySubjectAudits = new ArrayList();
        ArrayList <AuditBean>eventCRFAudits = new ArrayList();
        ArrayList studyEventAudits = new ArrayList();
        ArrayList allDeletedEventCRFs = new ArrayList();
        ArrayList allEventCRFs = new ArrayList();
        ArrayList allEventCRFItems = new ArrayList();
        String attachedFilePath = Utils.getAttachedFilePath(currentStudy);

        FormProcessor fp = new FormProcessor(request);

        int studySubId = fp.getInt("id", true);

        if (studySubId == 0) {
            addPageMessage(respage.getString("please_choose_a_subject_to_view"));
            forwardPage(Page.LIST_STUDY_SUBJECTS);
        } else {
            studySubject = (StudySubjectBean) subdao.findByPK(studySubId);
            Study study = (Study) getStudyDao().findByPK(studySubject.getStudyId());
            // Check if this StudySubject would be accessed from the Current Study
            if (studySubject.getStudyId() != currentStudy.getStudyId()) {
                if (currentStudy.isSite()) {
                    addPageMessage(respage.getString("no_have_correct_privilege_current_study") + " " + respage.getString("change_active_study_or_contact"));
                    forwardPage(Page.MENU_SERVLET);
                    return;
                } else {
                    // The SubjectStudy is not belong to currentstudy and current study is not a site.
                    Collection sites = getStudyDao().findOlnySiteIdsByStudy(currentStudy);
                    if (!sites.contains(study.getStudyId())) {
                        addPageMessage(
                                respage.getString("no_have_correct_privilege_current_study") + " " + respage.getString("change_active_study_or_contact"));
                        forwardPage(Page.MENU_SERVLET);
                        return;
                    }
                }
            }

            subject = (SubjectBean) sdao.findByPK(studySubject.getSubjectId());

            /* Show both study subject and subject audit events together */
            // Study subject value changed
            Collection studySubjectAuditEvents = adao.findStudySubjectAuditEvents(studySubject.getId());
            // Text values will be shown on the page for the corresponding
            // integer values.
            Role role = currentRole.getRole();

            for (Iterator iterator = studySubjectAuditEvents.iterator(); iterator.hasNext();) {
                AuditBean auditBean = (AuditBean) iterator.next();
                if (auditBean.getAuditEventTypeId() == 3) {
                    auditBean.setOldValue(Status.get(Integer.parseInt(auditBean.getOldValue())).getName());
                    auditBean.setNewValue(Status.get(Integer.parseInt(auditBean.getNewValue())).getName());
                }
                if (getAuditLogEventTypes().contains(auditBean.getAuditEventTypeId())) {
                    auditBean.setOldValue("<Masked>");
                    auditBean.setNewValue("<Masked>");
                }
            }
            studySubjectAudits.addAll(studySubjectAuditEvents);

            // Global subject value changed
            studySubjectAudits.addAll(adao.findSubjectAuditEvents(subject.getId()));

            studySubjectAudits.addAll(adao.findStudySubjectGroupAssignmentAuditEvents(studySubject.getId()));

            // Get the list of events
            events = studyEventDAO.findAllByStudySubject(studySubject);
            for (int i = 0; i < events.size(); i++) {
                // Link study event definitions
                StudyEventBean studyEvent = (StudyEventBean) events.get(i);
                studyEvent.setStudyEventDefinition((StudyEventDefinitionBean) seddao.findByPK(studyEvent.getStudyEventDefinitionId()));

                // Link event CRFs
                studyEvent.setEventCRFs(eventCRFDAO.findAllByStudyEvent(studyEvent));

                // Find deleted Event CRFs
                List deletedEventCRFs = adao.findDeletedEventCRFsFromAuditEvent(studyEvent.getId());
                allDeletedEventCRFs.addAll(deletedEventCRFs);
                List eventCRFs = (List) adao.findAllEventCRFAuditEvents(studyEvent.getId());
                allEventCRFs.addAll(eventCRFs);
                List eventCRFItems = (List) adao.findAllEventCRFAuditEventsWithItemDataType(studyEvent.getId());
                allEventCRFItems.addAll(eventCRFItems);
                logger.info("deletedEventCRFs size[" + deletedEventCRFs.size() + "]");
                logger.info("allEventCRFItems size[" + allEventCRFItems.size() + "]");
            }

            for (int i = 0; i < events.size(); i++) {
                StudyEventBean studyEvent = (StudyEventBean) events.get(i);
                studyEventAudits.addAll(adao.findStudyEventAuditEvents(studyEvent.getId()));

                ArrayList eventCRFs = studyEvent.getEventCRFs();
                for (int j = 0; j < eventCRFs.size(); j++) {
                    // Link CRF and CRF Versions
                    EventCRFBean eventCRF = (EventCRFBean) eventCRFs.get(j);
                    eventCRF.setFormLayout((FormLayoutBean) fldao.findByPK(eventCRF.getFormLayoutId()));
                    // Get the event crf audits
                    CRFBean crf =cdao.findByLayoutId(eventCRF.getFormLayoutId());
                    StudyEventDefinitionBean sed = (StudyEventDefinitionBean)seddao.findByPK(studyEvent.getStudyEventDefinitionId());
                    eventCRF.setCrf(crf);

                    List<String> tagIds = getPermissionTagsList().size()!=0 ?getPermissionTagsList():new ArrayList<>();

                    List < AuditBean> abs= (List<AuditBean>) adao.findEventCRFAuditEventsWithItemDataType(eventCRF.getId());
                    for (AuditBean ab : abs) {
                        if (ab.getAuditTable().equalsIgnoreCase("item_data")) {
                            EventDefinitionCRFBean edc = edcdao.findByStudyEventDefinitionIdAndCRFId(study, sed.getId(), crf.getId());
                            List <EventDefinitionCrfPermissionTag> edcPTagIds= eventDefinitionCrfPermissionTagDao.findByEdcIdTagId(edc.getId(), edc.getParentId(),tagIds);

                            if(edcPTagIds.size()!=0){
                                ab.setOldValue("<Masked>");
                                ab.setNewValue("<Masked>");                            }
                        }
                        ab.setStudyEventId(studyEvent.getId());
                        ab.setEventCrfVersionId(eventCRF.getFormLayoutId());
                    }

                    eventCRFAudits.addAll(abs);
                    logger.info("eventCRFAudits size [" + eventCRFAudits.size() + "] eventCRF id [" + eventCRF.getId() + "]");
                }
            }
            ItemDataDAO itemDataDao = new ItemDataDAO(sm.getDataSource());
            for (Object o : eventCRFAudits) {
                AuditBean ab = (AuditBean) o;
                if (ab.getAuditTable().equalsIgnoreCase("item_data")) {
                    ItemDataBean idBean = (ItemDataBean) itemDataDao.findByPK(ab.getEntityId());
                    ab.setOrdinal(idBean.getOrdinal());
                }
            }

        }

        try {

            WritableFont headerFormat = new WritableFont(WritableFont.ARIAL, 8, WritableFont.BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLUE2);
            WritableCellFormat cellFormat = new WritableCellFormat();
            cellFormat.setFont(headerFormat);

            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-Disposition", "attachment; filename=export.xls");

            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setLocale(new Locale("en", "EN"));
            WritableWorkbook workbook = Workbook.createWorkbook(response.getOutputStream(), wbSettings);

            int row = 0;

            // Subject Information
            workbook.createSheet("Subject Information", 0);
            WritableSheet excelSheet = workbook.getSheet(0);
            // Subject Summary
            String[] excelRow = new String[] { "study_subject_ID", "created_by", "status" };
            for (int i = 0; i < excelRow.length; i++) {
                Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                excelSheet.addCell(label);
            }
            row++;

            excelRow = new String[] { studySubject.getLabel(), studySubject.getOwner().getName(), studySubject.getStatus().getName() };
            for (int i = 0; i < excelRow.length; i++) {
                Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                excelSheet.addCell(label);
            }
            row++;
            row++;

            // Subject Audit Events
            excelRow = new String[] { "audit_event", "date_time_of_server", "user", "value_type", "old", "new" };
            for (int i = 0; i < excelRow.length; i++) {
                Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                excelSheet.addCell(label);
            }
            row++;

            for (int j = 0; j < studySubjectAudits.size(); j++) {
                AuditBean audit = (AuditBean) studySubjectAudits.get(j);

                excelRow = new String[] { audit.getAuditEventTypeName(), dateTimeFormat(audit.getAuditDate()), audit.getUserName(), audit.getEntityName(),
                        audit.getOldValue(), audit.getNewValue() };
                for (int i = 0; i < excelRow.length; i++) {
                    Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i].replace(" ", "_").toLowerCase()), cellFormat);
                    excelSheet.addCell(label);
                }
                row++;
            }
            row++;

            // Study Events
            excelRow = new String[] { "study_events", "location", "date", "occurrence_number" };
            for (int i = 0; i < excelRow.length; i++) {
                Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                excelSheet.addCell(label);
            }
            row++;

            for (int j = 0; j < events.size(); j++) {
                StudyEventBean event = (StudyEventBean) events.get(j);
                if (event.getStartTimeFlag()) {
                    excelRow = new String[] { event.getStudyEventDefinition().getName(), event.getLocation(), dateTimeFormat(event.getDateStarted()),
                            Integer.toString(event.getSampleOrdinal()) };
                } else {
                    excelRow = new String[] { event.getStudyEventDefinition().getName(), event.getLocation(), dateFormat(event.getDateStarted()),
                            Integer.toString(event.getSampleOrdinal()) };
                }
                for (int i = 0; i < excelRow.length; i++) {
                    Label label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                    excelSheet.addCell(label);
                }
                row++;
            }
            autoSizeColumns(excelSheet);

            int sheet = 0;

            // Study Event Summary Looper
            for (int eventCount = 0; eventCount < events.size(); eventCount++) {
                row = 0;
                sheet++;
                StudyEventBean event = (StudyEventBean) events.get(eventCount);
                workbook.createSheet(event.getStudyEventDefinition().getName().replace("/", ".") + "_" + event.getSampleOrdinal(), sheet);
                excelSheet = workbook.getSheet(sheet);

                Label label = null;

                // Header
                label = new Label(0, row, ResourceBundleProvider.getResWord("name"), cellFormat);
                excelSheet.addCell(label);
                label = new Label(1, row, event.getStudyEventDefinition().getName(), cellFormat);
                excelSheet.addCell(label);
                row++;
                label = new Label(0, row, "Location");
                excelSheet.addCell(label);
                label = new Label(1, row, event.getLocation());
                excelSheet.addCell(label);
                row++;
                label = new Label(0, row, "Start Date");
                excelSheet.addCell(label);
                if (event.getStartTimeFlag()) {
                    label = new Label(1, row, dateTimeFormat(event.getDateStarted()));
                } else {
                    label = new Label(1, row, dateFormat(event.getDateStarted()));
                }
                excelSheet.addCell(label);
                row++;
                label = new Label(0, row, "Status");
                excelSheet.addCell(label);
                label = new Label(1, row, event.getWorkflowStatus().toString());
                excelSheet.addCell(label);
                row++;
                label = new Label(0, row, ResourceBundleProvider.getResWord("occurrence_number"));
                excelSheet.addCell(label);
                label = new Label(1, row, Integer.toString(event.getSampleOrdinal()));
                excelSheet.addCell(label);
                row++;
                row++;
                // End Header

                // Audit for Deleted Event CRFs
                excelRow = new String[] { "name", "version", "deleted_by", "delete_date" };
                for (int i = 0; i < excelRow.length; i++) {
                    label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                    excelSheet.addCell(label);
                }
                row++;

                for (int j = 0; j < allDeletedEventCRFs.size(); j++) {
                    DeletedEventCRFBean deletedEventCRF = (DeletedEventCRFBean) allDeletedEventCRFs.get(j);
                    if (deletedEventCRF.getStudyEventId() == event.getId()) {
                        excelRow = new String[] { deletedEventCRF.getCrfName(), deletedEventCRF.getFormLayout(), deletedEventCRF.getDeletedBy(),
                                dateFormat(deletedEventCRF.getDeletedDate()) };
                        for (int i = 0; i < excelRow.length; i++) {
                            label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                            excelSheet.addCell(label);
                        }
                        row++;
                    }
                }
                row++;
                row++;

                // Audit Events for Study Event
                excelRow = new String[] { "audit_event", "date_time_of_server", "user", "value_type", "old", "new", "details" };
                for (int i = 0; i < excelRow.length; i++) {
                    label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                    excelSheet.addCell(label);
                }
                row++;

                for (int j = 0; j < studyEventAudits.size(); j++) {
                    AuditBean studyEvent = (AuditBean) studyEventAudits.get(j);
                    if (studyEvent.getEntityId() == event.getId()) {
                        String oldValue = studyEvent.getOldValue();
                        String newValue = studyEvent.getNewValue();
                        String entityName=studyEvent.getEntityName();
                        if (entityName.equals("Status")) {
                            if (oldValue.equals("1") || oldValue.equals(StudyEventWorkflowStatusEnum.SCHEDULED))
                                oldValue = StudyEventWorkflowStatusEnum.SCHEDULED.getDisplayValue();
                            else if (oldValue.equals("2") || oldValue.equals(StudyEventWorkflowStatusEnum.NOT_SCHEDULED))
                                oldValue = StudyEventWorkflowStatusEnum.NOT_SCHEDULED.getDisplayValue();
                            else if (oldValue.equals("3") || oldValue.equals(StudyEventWorkflowStatusEnum.DATA_ENTRY_STARTED))
                                oldValue = StudyEventWorkflowStatusEnum.DATA_ENTRY_STARTED.getDisplayValue();
                            else if (oldValue.equals("4") || oldValue.equals(StudyEventWorkflowStatusEnum.COMPLETED))
                                oldValue = StudyEventWorkflowStatusEnum.COMPLETED.getDisplayValue();
                            else if (oldValue.equals("5") || oldValue.equals(StudyEventWorkflowStatusEnum.STOPPED))
                                oldValue = StudyEventWorkflowStatusEnum.STOPPED.getDisplayValue();
                            else if (oldValue.equals("6") || oldValue.equals(StudyEventWorkflowStatusEnum.SKIPPED))
                                oldValue = StudyEventWorkflowStatusEnum.SKIPPED.getDisplayValue();
                            else if (oldValue.equals("7"))
                                oldValue = "locked";
                            else if (oldValue.equals("8"))
                                oldValue = "signed";


                            if (newValue.equals("1") || newValue.equals(StudyEventWorkflowStatusEnum.SCHEDULED))
                                newValue = StudyEventWorkflowStatusEnum.SCHEDULED.getDisplayValue();
                            else if (newValue.equals("2") || newValue.equals(StudyEventWorkflowStatusEnum.NOT_SCHEDULED))
                                newValue = StudyEventWorkflowStatusEnum.NOT_SCHEDULED.getDisplayValue();
                            else if (newValue.equals("3") || newValue.equals(StudyEventWorkflowStatusEnum.DATA_ENTRY_STARTED))
                                newValue = StudyEventWorkflowStatusEnum.DATA_ENTRY_STARTED.getDisplayValue();
                            else if (newValue.equals("4") || newValue.equals(StudyEventWorkflowStatusEnum.COMPLETED))
                                newValue = StudyEventWorkflowStatusEnum.COMPLETED.getDisplayValue();
                            else if (newValue.equals("5") || newValue.equals(StudyEventWorkflowStatusEnum.STOPPED))
                                newValue = StudyEventWorkflowStatusEnum.STOPPED.getDisplayValue();
                            else if (newValue.equals("6") || newValue.equals(StudyEventWorkflowStatusEnum.SKIPPED))
                                newValue = StudyEventWorkflowStatusEnum.SKIPPED.getDisplayValue();
                            else if (newValue.equals("7"))
                                newValue = "locked";
                            else if (newValue.equals("8") )
                                newValue = "signed";
                        }else if(entityName.equals("Archived")||entityName.equals("Removed") ||entityName.equals("Locked")||entityName.equals("Signed") ){
                            if (oldValue.equals("true"))
                                oldValue = "Yes";
                            else if (oldValue.equals("false"))
                                oldValue = "No";
                            if (newValue.equals("true"))
                                newValue = "Yes";
                            else if (newValue.equals("false"))
                                newValue = "No";
                        }else{
                            newValue=newValue.toLowerCase();
                        }
                                excelRow = new String[]{studyEvent.getAuditEventTypeName(), dateTimeFormat(studyEvent.getAuditDate()), studyEvent.getUserName(),
                                    entityName + "(" + studyEvent.getOrdinal() + ")", oldValue, newValue, studyEvent.getDetails()};
                            for (int i = 0; i < excelRow.length; i++) {
                                label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                                excelSheet.addCell(label);
                            }
                            row++;
                      //  }
                    }
                }
                row++;
                row++;

                // Event CRFs Audit Events
                for (int j = 0; j < allEventCRFs.size(); j++) {
                    AuditBean auditBean = (AuditBean) allEventCRFs.get(j);
                    EventCRFBean eventCrf = (EventCRFBean) eventCRFDAO.findByPK(auditBean.getEventCRFId());
                    FormLayoutBean formLayout = (FormLayoutBean) fldao.findByPK(eventCrf.getFormLayoutId());
                    if (auditBean.getStudyEventId() == event.getId()) {

                        // Audit Events for Study Event
                        excelRow = new String[] { "name", "version", "date_interviewed", "interviewer_name", "owner" };

                        for (int i = 0; i < excelRow.length; i++) {
                            label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                            excelSheet.addCell(label);
                        }
                        row++;

                        excelRow = new String[] { auditBean.getCrfName(), formLayout.getName(), dateFormat(eventCrf.getDateInterviewed()),
                                eventCrf.getInterviewerName(), eventCrf.getOwner().getName() };
                        for (int i = 0; i < excelRow.length; i++) {
                            label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                            excelSheet.addCell(label);
                        }
                        row++;
                        row++;

                        excelRow = new String[] { "audit_event", "date_time_of_server", "user", "value_type", "old", "new" };
                        for (int i = 0; i < excelRow.length; i++) {
                            label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                            excelSheet.addCell(label);
                        }
                        row++;
                        row++;
                        for (int k = 0; k < eventCRFAudits.size(); k++) {
                            row--;
                            AuditBean eventCrfAudit = (AuditBean) eventCRFAudits.get(k);
                            if (eventCrfAudit.getStudyEventId() == event.getId() && eventCrfAudit.getEventCrfVersionId() == auditBean.getEventCrfVersionId()) {
                                String oldValue = eventCrfAudit.getOldValue();
                                String newValue = eventCrfAudit.getNewValue();
                                String entityName= eventCrfAudit.getEntityName();
                                if (entityName.equals("Status")) {
                                    if (oldValue.equals("1") || oldValue.equals(EventCrfWorkflowStatusEnum.INITIAL_DATA_ENTRY))
                                        oldValue = EventCrfWorkflowStatusEnum.INITIAL_DATA_ENTRY.getDisplayValue();
                                    else if (oldValue.equals("2") || oldValue.equals(EventCrfWorkflowStatusEnum.COMPLETED))
                                        oldValue = EventCrfWorkflowStatusEnum.COMPLETED.getDisplayValue();
                                    else if (oldValue.equals(EventCrfWorkflowStatusEnum.NOT_STARTED))
                                        oldValue = EventCrfWorkflowStatusEnum.NOT_STARTED.getDisplayValue();

                                } else if (eventCrfAudit.getAuditEventTypeId() == 32) {
                                    if (oldValue.equals("0"))
                                        oldValue = "FALSE";
                                    else if (oldValue.equals("1"))
                                        oldValue = "TRUE";
                                }else if(entityName.equals("Archived")||entityName.equals("Removed")  ){
                                    if (oldValue.equals("true"))
                                        oldValue = "Yes";
                                    else if (oldValue.equals("false"))
                                        oldValue = "No";

                                }else{
                                    oldValue=oldValue.toLowerCase();
                                }


                                if (entityName.equals("Status")) {
                                    if (newValue.equals("1") || newValue.equals(EventCrfWorkflowStatusEnum.INITIAL_DATA_ENTRY))
                                        newValue = EventCrfWorkflowStatusEnum.INITIAL_DATA_ENTRY.getDisplayValue();
                                    else if (newValue.equals("2") || newValue.equals(EventCrfWorkflowStatusEnum.COMPLETED))
                                        newValue = EventCrfWorkflowStatusEnum.COMPLETED.getDisplayValue();
                                    else if (newValue.equals(EventCrfWorkflowStatusEnum.NOT_STARTED))
                                        newValue = EventCrfWorkflowStatusEnum.NOT_STARTED.getDisplayValue();

                                } else if (eventCrfAudit.getAuditEventTypeId() == 32) {
                                    if (newValue.equals("0"))
                                        newValue = "FALSE";
                                    else if (newValue.equals("1"))
                                        newValue = "TRUE";
                                } else if (entityName.equals("Archived") || entityName.equals("Removed")) {
                                    if (newValue.equals("true"))
                                        newValue = "Yes";
                                    else if (newValue.equals("false"))
                                        newValue = "No";
                                } else {
                                    newValue = newValue.toLowerCase();
                                }


                            String ordinal = "";
                                if (eventCrfAudit.getOrdinal() != 0) {
                                    ordinal = "(" + eventCrfAudit.getOrdinal() + ")";
                                } else if (eventCrfAudit.getOrdinal() == 0 && eventCrfAudit.getItemDataRepeatKey() != 0) {
                                    ordinal = "(" + eventCrfAudit.getItemDataRepeatKey() + ")";
                                }

                                excelRow = new String[] { eventCrfAudit.getAuditEventTypeName(), dateTimeFormat(eventCrfAudit.getAuditDate()),
                                        eventCrfAudit.getUserName(), entityName + ordinal, oldValue, newValue };
                                for (int i = 0; i < excelRow.length; i++) {
                                    label = new Label(i, row, ResourceBundleProvider.getResWord(excelRow[i]), cellFormat);
                                    excelSheet.addCell(label);
                                }
                                row++;
                                row++;
                            }

                            row++;

                        }
                        row++;

                    }
                    autoSizeColumns(excelSheet);
                }
            }

            workbook.write();
            workbook.close();
            session.setAttribute("subject", null);
            session.setAttribute("studySub", null);
            session.setAttribute("studyEventAudits", null);
            session.setAttribute("studySubjectAudits", null);
            session.setAttribute("events", null);
            session.setAttribute("eventCRFAudits", null);
            session.setAttribute("allDeletedEventCRFs", null);
        } catch (Exception e) {
            throw e;
        } finally {
            // proposed move session attributes here

        }
    }

    @Override
    protected String getAdminServlet() {
        if (ub.isSysAdmin()) {
            return SecureController.ADMIN_SERVLET_CODE;
        } else {
            return "";
        }
    }

    private String dateFormat(Date date) {
        if (date == null) {
            return "";
        } else {
            SimpleDateFormat dteFormat = new SimpleDateFormat(ResourceBundleProvider.getFormatBundle().getString("date_format_string"));
            return dteFormat.format(date);
        }
    }

    private String dateTimeFormat(Date date) {
        if (date == null) {
            return "";
        } else {
            SimpleDateFormat dtetmeFormat = new SimpleDateFormat(ResourceBundleProvider.getFormatBundle().getString("date_time_format_string"));
            return dtetmeFormat.format(date);
        }
    }

    private void autoSizeColumns(WritableSheet sheet) {
        for (int x = 0; x < 6; x++) {
            CellView cell = sheet.getColumnView(x);
            cell.setAutosize(true);
            sheet.setColumnView(x, cell);
        }
    }
    private List<Integer> getAuditLogEventTypes() {
        List<Integer> auditLogEventTypes = new ArrayList<>();
        auditLogEventTypes.add(43);
        auditLogEventTypes.add(44);
        auditLogEventTypes.add(46);
        auditLogEventTypes.add(47);
        auditLogEventTypes.add(49);
        auditLogEventTypes.add(50);
        auditLogEventTypes.add(52);
        auditLogEventTypes.add(53);
        auditLogEventTypes.add(55);
        auditLogEventTypes.add(56);
        return auditLogEventTypes;
    }
}
