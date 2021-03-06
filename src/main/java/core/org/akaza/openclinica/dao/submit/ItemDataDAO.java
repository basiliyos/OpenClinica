/*
 * OpenClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: http://www.openclinica.org/license
 * copyright 2003-2005 Akaza Research
 */
package core.org.akaza.openclinica.dao.submit;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.sql.DataSource;

import core.org.akaza.openclinica.bean.core.*;
import core.org.akaza.openclinica.bean.submit.CRFVersionBean;
import core.org.akaza.openclinica.bean.submit.EventCRFBean;
import core.org.akaza.openclinica.bean.submit.ItemBean;
import core.org.akaza.openclinica.bean.submit.ItemDataBean;
import core.org.akaza.openclinica.bean.submit.ItemGroupBean;
import core.org.akaza.openclinica.bean.submit.SectionBean;
import core.org.akaza.openclinica.core.form.StringUtil;
import core.org.akaza.openclinica.dao.core.AuditableEntityDAO;
import core.org.akaza.openclinica.dao.core.DAODigester;
import core.org.akaza.openclinica.dao.core.SQLFactory;
import core.org.akaza.openclinica.dao.core.TypeNames;
import core.org.akaza.openclinica.i18n.util.I18nFormatUtil;
import core.org.akaza.openclinica.i18n.util.ResourceBundleProvider;

/**
 * <P>
 * ItemDataDAO.java, the equivalent to AnswerDAO in the original code base. If item is date, item data value has to be
 * saved into database as specified in ISO 8601.
 * 
 * @author thickerson
 * 
 * 
 */
public class ItemDataDAO extends AuditableEntityDAO {

    boolean formatDates = true;

    // YW 12-06-2007 <<!!! Be careful when there is item with data-type as
    // "Date".
    // You have to make sure that string pattern conversion has been done before
    // you insert/update items into database
    // or once you fetched items from database.
    // The correct patterns are:
    // in database, it should be oc_date_format_string
    // in application, it should be local date_format_string
    // If your method makes use of "getEntityFromHashMap", conversion has been
    // handled.
    // And as at this point, "getEntityFromHashMap" is used for fetched data
    // from database,
    // conversion is from oc_date_format pattern to local date_format pattern.
    // YW >>

    public boolean isFormatDates() {
        return formatDates;
    }

    public void setFormatDates(boolean formatDates) {
        this.formatDates = formatDates;
    }

    public Collection findMinMaxDates() {
        ArrayList al = new ArrayList();
        ArrayList alist = this.select(digester.getQuery("findMinMaxDates"));
        // al =
        return al;
    }

    // private DAODigester digester;

    private void setQueryNames() {
        getCurrentPKName = "getCurrentPK";
        getNextPKName = "getNextPK";
    }

    public ItemDataDAO(DataSource ds) {
        super(ds);
        setQueryNames();
        if (this.locale == null) {
            this.locale = ResourceBundleProvider.getLocale(); // locale still might be null.
        }
    }

    public ItemDataDAO(DataSource ds, Locale locale) {
        super(ds);
        setQueryNames();
        if (locale != null) {
            this.locale = locale;
        } else {
            this.locale = ResourceBundleProvider.getLocale();
        }
        if (this.locale != null) {
            local_df_string = ResourceBundleProvider.getFormatBundle(this.locale).getString("date_format_string");
        }
    }

    public ItemDataDAO(DataSource ds, DAODigester digester) {
        super(ds);
        this.digester = digester;
        setQueryNames();
    }

    // This constructor sets up the Locale for JUnit tests; see the locale
    // member variable in EntityDAO, and its initializeI18nStrings() method
    public ItemDataDAO(DataSource ds, DAODigester digester, Locale locale) {

        this(ds, digester);
        this.locale = locale;
    }

    @Override
    protected void setDigesterName() {
        digesterName = SQLFactory.getInstance().DAO_ITEMDATA;
    }

    @Override
    public void setTypesExpected() {
        this.unsetTypeExpected();
        this.setTypeExpected(1, TypeNames.INT);
        this.setTypeExpected(2, TypeNames.INT);
        this.setTypeExpected(3, TypeNames.INT);
//        this.setTypeExpected(4, TypeNames.INT);
        this.setTypeExpected(4, TypeNames.STRING);
        this.setTypeExpected(5, TypeNames.DATE);
        this.setTypeExpected(6, TypeNames.DATE);
        this.setTypeExpected(7, TypeNames.INT);// owner id
        this.setTypeExpected(8, TypeNames.INT);// update id
        this.setTypeExpected(9, TypeNames.INT);// ordinal
//        this.setTypeExpected(11, TypeNames.INT);// old_status_id
        this.setTypeExpected(10, TypeNames.BOOL);// ocform_deleted
    }

    public EntityBean update(EntityBean eb) {
        ItemDataBean idb = (ItemDataBean) eb;

        // YW 12-06-2007 << convert to oc_date_format_string pattern before
        // inserting into database
        ItemDataType dataType = getDataType(idb.getItemId());
        if (dataType.equals(ItemDataType.DATE)) {
            idb.setValue(Utils.convertedItemDateValue(idb.getValue(), local_df_string, oc_df_string, locale));
        } else if (dataType.equals(ItemDataType.PDATE)) {
            idb.setValue(formatPDate(idb.getValue()));
        }
        idb.setActive(false);

        HashMap<Integer, Comparable> variables = new HashMap<Integer, Comparable>();
        variables.put(new Integer(1), new Integer(idb.getEventCRFId()));
        variables.put(new Integer(2), new Integer(idb.getItemId()));
        variables.put(new Integer(3), idb.getValue());
        variables.put(new Integer(4), new Integer(idb.getUpdaterId()));
        variables.put(new Integer(5), new Integer(idb.getOrdinal()));
        variables.put(new Integer(6), new Boolean(idb.isDeleted()));
        variables.put(new Integer(7), new Integer(idb.getId()));
        this.execute(digester.getQuery("update"), variables);

        if (isQuerySuccessful()) {
            idb.setActive(true);
        }

        return idb;
    }

    /**
     * this will update item data status
     */


    /*
     * current_df_string= yyyy-MM-dd oc_df_string = yyyy-mm-dd local_df_string = dd-MMM-yyyy
     */
    public ItemDataBean setItemDataBeanIfDateOrPdate(ItemDataBean idb, String current_df_string, ItemDataType dataType) {
        if (dataType.equals(ItemDataType.DATE)) {
            idb.setValue(Utils.convertedItemDateValue(idb.getValue(), current_df_string, oc_df_string, locale));
        } else if (dataType.equals(ItemDataType.PDATE)) {
            idb.setValue(formatPDate(idb.getValue()));
        }
        return idb;
    }


    public EntityBean create(EntityBean eb) {
        ItemDataBean idb = (ItemDataBean) eb;
        // YW 12-06-2007 << convert to oc_date_format_string pattern before
        // inserting into database
        ItemDataType dataType = getDataType(idb.getItemId());
        if (dataType.equals(ItemDataType.DATE)) {
            idb.setValue(Utils.convertedItemDateValue(idb.getValue(), local_df_string, oc_df_string, locale));
        } else if (dataType.equals(ItemDataType.PDATE)) {
            idb.setValue(formatPDate(idb.getValue()));
        }

        HashMap<Integer, Comparable> variables = new HashMap<Integer, Comparable>();
        int id = getNextPK();
        variables.put(new Integer(1), new Integer(id));
        variables.put(new Integer(2), new Integer(idb.getEventCRFId()));
        variables.put(new Integer(3), new Integer(idb.getItemId()));
        variables.put(new Integer(4), idb.getValue());
        variables.put(new Integer(5), new Integer(idb.getOwnerId()));
        variables.put(new Integer(6), new Integer(idb.getOrdinal()));
        variables.put(new Integer(7), new Boolean(idb.isDeleted()));
        this.execute(digester.getQuery("create"), variables);

        if (isQuerySuccessful()) {
            idb.setId(id);
        }

        return idb;
    }

    /*
     * Small check to make sure the type is a date, tbh
     */
    public ItemDataType getDataType(int itemId) {
        ItemDAO itemDAO = new ItemDAO(this.getDs());
        ItemBean itemBean = (ItemBean) itemDAO.findByPK(itemId);
        return itemBean.getDataType();
    }

    // public boolean isPDateType(int itemId) {
    // ItemDAO itemDAO = new ItemDAO(this.getDs());
    // ItemBean itemBean = (ItemBean)itemDAO.findByPK(itemId);
    // if (itemBean.getDataType().equals(ItemDataType.PDATE)) {
    // return true;
    // }
    // return false;
    //
    // }

    public String formatPDate(String pDate) {
        String temp = "";
        if (pDate != null && pDate.length() > 0) {
            String yearMonthFormat = I18nFormatUtil.yearMonthFormatString(this.locale);
            String yearFormat = I18nFormatUtil.yearFormatString();
            String dateFormat = I18nFormatUtil.dateFormatString(this.locale);
            try {
                if (StringUtil.isFormatDate(pDate, dateFormat, this.locale)) {
                    temp = new SimpleDateFormat(oc_df_string, this.locale).format(new SimpleDateFormat(dateFormat, this.locale).parse(pDate));
                } else if (StringUtil.isPartialYear(pDate, yearFormat, this.locale)) {
                    temp = pDate;
                } else if (StringUtil.isPartialYearMonth(pDate, yearMonthFormat, this.locale)) {
                    temp = new SimpleDateFormat(ApplicationConstants.getPDateFormatInSavedData(), this.locale).format(new SimpleDateFormat(yearMonthFormat,
                            this.locale).parse(pDate));
                }
            } catch (Exception ex) {
                logger.warn("Parsial Date Parsing Exception........");
            }
        }
        return temp;
    }

    public String reFormatPDate(String pDate) {
        String temp = "";
        if (pDate != null && pDate.length() > 0) {
            String yearMonthFormat = I18nFormatUtil.yearMonthFormatString(this.locale);
            String dateFormat = I18nFormatUtil.dateFormatString(this.locale);
            try {
                if (StringUtil.isFormatDate(pDate, oc_df_string, this.locale)) {
                    temp = new SimpleDateFormat(dateFormat, this.locale).format(new SimpleDateFormat(oc_df_string, this.locale).parse(pDate));
                } else if (StringUtil.isPartialYear(pDate, "yyyy", this.locale)) {
                    temp = pDate;
                } else if (StringUtil.isPartialYearMonth(pDate, ApplicationConstants.getPDateFormatInSavedData(), this.locale)) {
                    temp = new SimpleDateFormat(yearMonthFormat, this.locale).format(new SimpleDateFormat(ApplicationConstants.getPDateFormatInSavedData(),
                            this.locale).parse(pDate));
                }
            } catch (Exception ex) {
                logger.warn("Parsial Date Parsing Exception........");
            }
        }
        return temp;
    }

    public Object getEntityFromHashMap(HashMap hm) {
        ItemDataBean eb = new ItemDataBean();
        this.setItemDataAuditInformation(eb, hm);
        eb.setId(((Integer) hm.get("item_data_id")).intValue());
        eb.setEventCRFId(((Integer) hm.get("event_crf_id")).intValue());
        eb.setItemId(((Integer) hm.get("item_id")).intValue());
        eb.setValue((String) hm.get("value"));
        // YW 12-06-2007 << since "getEntityFromHashMap" only be used for find
        // right now,
        // convert item date value to local_date_format_string pattern once
        // fetching out from database
        if (formatDates) {
            ItemDataType dataType = getDataType(eb.getItemId());
            if (dataType.equals(ItemDataType.DATE)) {
                eb.setValue(Utils.convertedItemDateValue(eb.getValue(), oc_df_string, local_df_string, locale));
            } else if (dataType.equals(ItemDataType.PDATE)) {
                eb.setValue(reFormatPDate(eb.getValue()));
            }
        }
        eb.setOrdinal(((Integer) hm.get("ordinal")).intValue());
        eb.setDeleted(((Boolean) hm.get("deleted")).booleanValue());
        return eb;
    }

    @SuppressWarnings("unchecked")
    public List<ItemDataBean> findByStudyEventAndOids(Integer studyEventId, String itemOid, String itemGroupOid) {
        setTypesExpected();

        HashMap<Integer, Object> variables = new HashMap<Integer, Object>();
        variables.put(new Integer(1), studyEventId);
        variables.put(new Integer(2), itemOid);
        variables.put(new Integer(3), itemGroupOid);


        ArrayList<ItemDataBean> dataItems = this.executeFindAllQuery("findByStudyEventAndOIDs", variables);
        return dataItems;
    }

    public Collection<ItemDataBean> findAll() {
        setTypesExpected();

        ArrayList alist = this.select(digester.getQuery("findAll"));
        ArrayList<ItemDataBean> al = new ArrayList<ItemDataBean>();
        Iterator it = alist.iterator();
        while (it.hasNext()) {
            ItemDataBean eb = (ItemDataBean) this.getEntityFromHashMap((HashMap) it.next());
            al.add(eb);
        }
        return al;
    }

    public Collection findAll(String strOrderByColumn, boolean blnAscendingSort, String strSearchPhrase) {
        ArrayList al = new ArrayList();

        return al;
    }

    public EntityBean findByPK(int ID) {
        ItemDataBean eb = new ItemDataBean();
        this.setTypesExpected();

        HashMap<Integer, Integer> variables = new HashMap<Integer, Integer>();
        variables.put(new Integer(1), new Integer(ID));

        String sql = digester.getQuery("findByPK");
        ArrayList alist = this.select(sql, variables);
        Iterator it = alist.iterator();

        if (it.hasNext()) {
            eb = (ItemDataBean) this.getEntityFromHashMap((HashMap) it.next());
        }
        return eb;
    }

    public void delete(int itemDataId) {
        HashMap<Integer, Comparable> variables = new HashMap<Integer, Comparable>();
        variables.put(new Integer(1), new Integer(itemDataId));

        this.execute(digester.getQuery("delete"), variables);
        return;

    }

    public Collection findAllByPermission(Object objCurrentUser, int intActionType, String strOrderByColumn, boolean blnAscendingSort, String strSearchPhrase) {
        ArrayList al = new ArrayList();

        return al;
    }

    public Collection findAllByPermission(Object objCurrentUser, int intActionType) {
        ArrayList al = new ArrayList();

        return al;
    }

    public ArrayList<ItemDataBean> findAllActiveBySectionIdAndEventCRFId(int sectionId, int eventCRFId) {
        setTypesExpected();
        HashMap<Integer, Object> variables = new HashMap<Integer, Object>();
        variables.put(new Integer(1), new Integer(sectionId));
        variables.put(new Integer(2), new Integer(eventCRFId));

        return this.executeFindAllQuery("findAllActiveBySectionIdAndEventCRFId", variables);
    }

    public ArrayList<ItemDataBean> findAllByEventCRFId(int eventCRFId) {
        setTypesExpected();
        HashMap<Integer, Object> variables = new HashMap<Integer, Object>();
        variables.put(new Integer(1), new Integer(eventCRFId));

        return this.executeFindAllQuery("findAllByEventCRFId", variables);
    }

    public ArrayList<ItemDataBean> findAllBlankRequiredByEventCRFId(int eventCRFId, int crfVersionId) {
        setTypesExpected();
        HashMap<Integer, Object> variables = new HashMap<Integer, Object>();
        variables.put(new Integer(1), new Integer(eventCRFId));
        variables.put(new Integer(2), new Integer(crfVersionId));

        return this.executeFindAllQuery("findAllBlankRequiredByEventCRFId", variables);
    }

    public ItemDataBean findByItemIdAndEventCRFId(int itemId, int eventCRFId) {
        setTypesExpected();
        ItemDataBean answer = new ItemDataBean();

        HashMap<Integer, Integer> variables = new HashMap<Integer, Integer>();
        variables.put(new Integer(1), new Integer(itemId));
        variables.put(new Integer(2), new Integer(eventCRFId));

        EntityBean eb = this.executeFindByPKQuery("findByItemIdAndEventCRFId", variables);

        if (!eb.isActive()) {
            return new ItemDataBean();
        } else {
            return (ItemDataBean) eb;
        }
    }

    // YW, 1-25-2008, for repeating item
    public ItemDataBean findByItemIdAndEventCRFIdAndOrdinal(int itemId, int eventCRFId, int ordinal) {
        setTypesExpected();
        ItemDataBean answer = new ItemDataBean();

        HashMap<Integer, Integer> variables = new HashMap<Integer, Integer>();
        variables.put(new Integer(1), new Integer(itemId));
        variables.put(new Integer(2), new Integer(eventCRFId));
        variables.put(new Integer(3), new Integer(ordinal));

        EntityBean eb = this.executeFindByPKQuery("findByItemIdAndEventCRFIdAndOrdinal", variables);

        if (!eb.isActive()) {
            return new ItemDataBean();// hmm, return null instead?
        } else {
            return (ItemDataBean) eb;
        }
    }

    public int findAllRequiredByEventCRFId(EventCRFBean ecb) {
        setTypesExpected();
        int answer = 0;
        HashMap<Integer, Integer> variables = new HashMap<Integer, Integer>();
        variables.put(new Integer(1), new Integer(ecb.getId()));
        String sql = digester.getQuery("findAllRequiredByEventCRFId");
        ArrayList rows = this.select(sql, variables);

        if (rows.size() > 0) {
            answer = rows.size();
        }

        return answer;
    }

	
	public ArrayList findSkipMatchCriterias(String sqlStr,ArrayList<String> skipMatchCriteriaOids) {
        setTypesExpected();
       
        ArrayList matchCriterias = new ArrayList<>();
       
        
        this.setTypeExpected(1, TypeNames.INT);
        this.setTypeExpected(2, TypeNames.STRING);
        this.setTypeExpected(3, TypeNames.STRING);
       
        Integer studyEventId;
        String itemOID;
        String itemValue;
        
        if(sqlStr == null || sqlStr.trim().length()==0) {
        	return matchCriterias;
        }
        
        ArrayList matchingItemDataQueryResults = this.select(sqlStr);
        int listSize = skipMatchCriteriaOids.size();
        
        
        if(listSize == 0) {        
        	return matchCriterias;
        }
        
        int i = 0;
        
        Iterator it = matchingItemDataQueryResults.iterator();
        
        Integer currentStudyEventId = null;
    	//OC-10832
        //initialize  HashMap skipMatchGroup 
        HashMap skipMatchGroup = new HashMap();
        for (String itemOid : skipMatchCriteriaOids) {
        	skipMatchGroup.put(itemOid, null);
        }
       
        while (it.hasNext()) {
        	HashMap hm = (HashMap) it.next();
        	i++;
        	
        	studyEventId = (Integer) hm.get("study_event_id");
        	itemOID = (String) hm.get("oc_oid");
        	itemValue = (String) hm.get("value");
        	// build row hash map to match data file line
        	if(currentStudyEventId == null) {
        		skipMatchGroup.put(itemOID, itemValue);
        		
        		if(i == listSize) {
        		
        			matchCriterias.add(skipMatchGroup);
        			i = 0;
        		}
        	}else if(currentStudyEventId.intValue()==studyEventId.intValue()) {
        		skipMatchGroup.put(itemOID, itemValue);
        		
        		// here added FULL skipMatchGroup --  all skip ItemOID has matched value
        		if(i == listSize) {
        			matchCriterias.add(skipMatchGroup);
        			//after added, clear the skipMatchGroup
        			i = 0;
        			skipMatchGroup = null;
        			
        		}
        	}else {
        		// added previous skipMatchGroup
        		if(skipMatchGroup !=null) {
        			matchCriterias.add(skipMatchGroup);
            		i = 0;	
        		}
        		        		
        		// after added previous,start new skipMatch row
        		skipMatchGroup = new HashMap();        		
        		for (String itemOid : skipMatchCriteriaOids) {
        	        	skipMatchGroup.put(itemOid, null);
        	     }
        		
        		skipMatchGroup.put(itemOID, itemValue);
        	}

        	currentStudyEventId = studyEventId;
        	
        }

        // Capture the last hash map.
        if (skipMatchGroup != null && !skipMatchGroup.isEmpty()){
            matchCriterias.add(skipMatchGroup);
        }
       
        return matchCriterias;
    }

    public void setItemDataAuditInformation(AuditableEntityBean aeb, HashMap hm) {
        // grab the required information from the table
        // so that we don't have to repeat this in every single dao
        Date dateCreated = (Date) hm.get("date_created");
        Date dateUpdated = (Date) hm.get("date_updated");
        Integer ownerId = (Integer) hm.get("owner_id");
        Integer updateId = (Integer) hm.get("update_id");

        if (aeb != null) {
            aeb.setCreatedDate(dateCreated);
            aeb.setUpdatedDate(dateUpdated);
            //This was throwing a ClassCastException : BWP altered in 4/2009
            aeb.setOwnerId(ownerId.intValue());
            aeb.setUpdaterId(updateId.intValue());
        }
    }
}