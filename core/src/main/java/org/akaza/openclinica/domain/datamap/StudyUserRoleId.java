// default package
// Generated Jul 31, 2013 2:03:33 PM by Hibernate Tools 3.4.0.CR1
package org.akaza.openclinica.domain.datamap;
import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * StudyUserRoleId generated by hbm2java
 */
@Embeddable
public class StudyUserRoleId implements Serializable {

	private Integer studyId;
	private String userName;

	public StudyUserRoleId() {
	}

	public StudyUserRoleId(String roleName, Integer studyId,
			String userName) {
		this.studyId = studyId;
		this.userName = userName;
	}

	@Column(name = "study_id")
	public Integer getStudyId() {
		return this.studyId;
	}

	public void setStudyId(Integer studyId) {
		this.studyId = studyId;
	}

	@Column(name = "user_name", length = 40)
	public String getUserName() {
		return this.userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public boolean equals(Object other) {
		if ((this == other))
			return true;
		if ((other == null))
			return false;
		if (!(other instanceof StudyUserRoleId))
			return false;
		StudyUserRoleId castOther = (StudyUserRoleId) other;

		return ((this.getStudyId() == castOther.getStudyId()) || (this
						.getStudyId() != null && castOther.getStudyId() != null && this
						.getStudyId().equals(castOther.getStudyId())))
				&& ((this.getUserName() == castOther.getUserName()) || (this
						.getUserName() != null
						&& castOther.getUserName() != null && this
						.getUserName().equals(castOther.getUserName())));
	}

	public int hashCode() {
		int result = 17;
		result = 37 * result
				+ (getStudyId() == null ? 0 : this.getStudyId().hashCode());
		result = 37 * result
				+ (getUserName() == null ? 0 : this.getUserName().hashCode());
		return result;
	}

}