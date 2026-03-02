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

export async function deleteRecord(recordID) {
  try {
    const response = await http.get(`/records/delete-record/${recordID}`);
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


export async function getRecordCountByDateLabelRange(startDate, endDate) {
  try {
    if (!startDate || !endDate) {
      throw new Error('Start and end date cannot be empty.');
    }
    const formData = {
      startDate: startDate,
      endDate: endDate
    }

    const response = await http.post('/records/record-count-by-date-label-in-range', formData);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export async function getTextQueryRecords(listDescribedRecordRequest) {
  try {
    const body = listDescribedRecordRequest.toApi();
    const response = await http.post("/records/get-records-by-description", body);
    return response.data;
  } catch (error) {
    console.log(error);
    return null;
  }
}

export class ListDescribedRecordRequest {
  constructor({
    user_id = null,
    query_text = "",
    limit = 20
  } = {}) {
    this.user_id = user_id;
    this.query_text = query_text;
    this.limit = limit;
  }

  toApi() {
    return {
      user_id: this.user_id,
      query_text: this.query_text,
      limit: this.limit
    }
  }
}

export class ListRecordRequest {
  constructor({
    titleContains = "",
    labels = [],
    excludeLabels = [],
    creationAfterDate = null, // yyyy-MM-dd
    creationBeforeDate = null,
    modifiedAfterDate = null,
    modifiedBeforeDate = null, 
    isCreatedByUserOnly = false,
    pageSize = 10,
    page = 0,
    sortBy = "creationTime"
  } = {}) {
    this.titleContains = titleContains;
    this.labels = labels;
    this.excludeLabels = excludeLabels;
    this.creationAfterDate = creationAfterDate;
    this.creationBeforeDate = creationBeforeDate;
    this.modifiedAfterDate = modifiedAfterDate;
    this.modifiedBeforeDate = modifiedBeforeDate;
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
      excludeLabels: this.excludeLabels,
      creationAfterDate: this.creationAfterDate,
      creationBeforeDate: this.creationBeforeDate,
      modifiedAfterDate: this.modifiedAfterDate,
      modifiedBeforeDate: this.modifiedBeforeDate,
      public: null,
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
    files = [],
    metadata = {}
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
    this.metadata = metadata;
  }

  toApi() {
    return {
      title: this.title,
      labels: this.labels,
      content: this.content,
      public: this.isPublic,
      alertType: this.alertType,
      alertTime: this.alertTime,
      recurringAlertWeekDays: this.recurringAlertWeekDays,
      metadata: this.metadata
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
    files = [],
    metadata = {}
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
    this.metadata = metadata;
  }

  toApi() {
    return {
      id: this.id,
      title: this.title,
      labels: this.labels,
      content: this.content,
      public: this.isPublic,
      alertType: this.alertType,
      alertTime: this.alertTime,
      cancelAlert: this.cancelAlert,
      recurringAlertWeekDays: this.recurringAlertWeekDays,
      removeFileIDs: this.removeFileIDs,
      metadata: this.metadata
    };
  }
}
