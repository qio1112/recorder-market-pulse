import http from './http'

export async function getRecords(listRecordRequest) {
  try {
    const body = listRecordRequest.toApi();
    const response = await http.post("/records/list-records", body);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export async function getRecordDetail(recordID) {
  try {
    const response = await http.get(`/records/record/${recordID}`);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export async function getRecFile(fileID) {
  try {
    const response = await http.get(`/recfile/${fileID}`, { responseType: 'blob' });
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export async function addNewRecord(addRecordRequest) {
  try {
    const jsonBody = addRecordRequest?.toApi ? addRecordRequest.toApi() : addRecordRequest;
    const formData = new FormData();
    formData.append('newRecordRequest', new Blob([JSON.stringify(jsonBody)], { type: 'application/json' }));

    const images = Array.isArray(addRecordRequest.images) ? addRecordRequest.images : [];
    const files = Array.isArray(addRecordRequest.files) ? addRecordRequest.files : [];

    images.forEach((file) => formData.append('images', file));
    files.forEach((file) => formData.append('files', file));
    const response = await http.post('/records/create-record', formData);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export async function editRecord(editRecordRequest) {
  try {
    const jsonBody = editRecordRequest?.toApi ? editRecordRequest.toApi() : editRecordRequest;
    const targetId = jsonBody.id || editRecordRequest.id;
    if (!targetId) {
      throw new Error('editRecordRequest.id is required');
    }
    const formData = new FormData();
    formData.append('updateRecordRequest', new Blob([JSON.stringify(jsonBody)], { type: 'application/json' }));

    const images = Array.isArray(editRecordRequest.images) ? editRecordRequest.images : [];
    const files = Array.isArray(editRecordRequest.files) ? editRecordRequest.files : [];

    images.forEach((file) => formData.append('images', file));
    files.forEach((file) => formData.append('files', file));

    const response = await http.post(`/records/update-record`, formData);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}


export class ListRecordRequest {
  constructor({
    titleContains = "",
    labels = [],
    creationAfterDate = null, // yyyy-MM-dd
    creationBeforeDate = null,
    modifiedAfterDate = null,
    modifiedBeforeDate = null, 
    isPublic = true,
    isCreatedByUserOnly = false,
    pageSize = 20,
    page = 0,
    sortBy = "creationTime"
  } = {}) {
    this.titleContains = titleContains;
    this.labels = labels;
    this.creationAfterDate = creationAfterDate;
    this.creationBeforeDate = creationBeforeDate;
    this.modifiedAfterDate = modifiedAfterDate;
    this.modifiedBeforeDate = modifiedBeforeDate;
    this.isPublic = isPublic;
    this.isCreatedByUserOnly = isCreatedByUserOnly;
    this.pageSize = pageSize;
    this.page = page;
    this.sortBy = sortBy;
  }

  // Convert to API request format (plain JSON)
  toApi() {
    return {
      titleContains: this.titleContains,
      labels: this.labels,
      creationAfterDate: this.creationAfterDate,
      creationBeforeDate: this.creationBeforeDate,
      modifiedAfterDate: this.modifiedAfterDate,
      modifiedBeforeDate: this.modifiedBeforeDate,
      isPublic: this.isPublic,
      isCreatedByUserOnly: this.isCreatedByUserOnly,
      pageSize: this.pageSize,
      page: this.page,
      sortBy: this.sortBy
    }
  }
}


export class AddRecordRequest {
  constructor({
    title = "",
    labels = [],
    content = "",
    isPublic = false,
    alertType = null,
    alertTime = null,
    recurringAlertWeekDays = null,
    images = [],
    files = []
  } = {}) {
    this.title = title;
    this.labels = labels;
    this.content = content;
    this.isPublic = isPublic;
    this.alertType = alertType; // ONE_TIME or RECURRING
    this.alertTime = alertTime; // e.g. "2025-10-11T17:04:15-04:00"
    this.recurringAlertWeekDays = recurringAlertWeekDays; // e.g. "MONDAY,TUESDAY,SATURDAY"
    this.images = images;
    this.files = files;
  }

  toApi() {
    return {
      title: this.title,
      labels: this.labels,
      content: this.content,
      isPublic: this.isPublic,
      alertType: this.alertType,
      alertTime: this.alertTime,
      recurringAlertWeekDays: this.recurringAlertWeekDays
    };
  }
}

export class EditRecordRequest {
  constructor({
    id = null,
    title = "",
    labels = [],
    content = "",
    isPublic = false,
    alertType = null,
    alertTime = null,
    cancelAlert = false,
    recurringAlertWeekDays = null,
    removeFileIDs = [], // list of recFile IDs (integer)
    images = [],
    files = []
  } = {}) {
    this.id = id;
    this.title = title;
    this.labels = labels;
    this.content = content;
    this.isPublic = isPublic;
    this.alertType = alertType; // ONE_TIME or RECURRING
    this.alertTime = alertTime; // e.g. "2025-10-11T17:04:15-04:00"
    this.cancelAlert = cancelAlert,
    this.recurringAlertWeekDays = recurringAlertWeekDays; // e.g. "MONDAY,TUESDAY,SATURDAY"
    this.removeFileIDs = removeFileIDs,
    this.images = images;
    this.files = files;
  }

  toApi() {
    return {
      id: this.id,
      title: this.title,
      labels: this.labels,
      content: this.content,
      isPublic: this.isPublic,
      alertType: this.alertType,
      alertTime: this.alertTime,
      cancelAlert: this.cancelAlert,
      recurringAlertWeekDays: this.recurringAlertWeekDays,
      removeFileIDs: this.removeFileIDs
    };
  }
}
