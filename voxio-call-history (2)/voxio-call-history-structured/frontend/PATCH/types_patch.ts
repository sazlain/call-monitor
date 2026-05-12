// AGREGAR a src/types/index.ts:

export interface CallHistoryResponse {
  id:                   number
  callId:               string
  callerExtension:      string | null
  callerIdNum:          string | null
  callerIdName:         string | null
  calledNumber:         string | null
  callStatus:           string | null
  callFlow:             string | null
  createdAt:            string
  agentId:              number | null
  agentName:            string | null
  agentExtension:       string | null
  typificationResult:   string | null
  typificationNotes:    string | null
  callbackDate:         string | null
  leadId:               number | null
  leadContactName:      string | null
  leadContactPhone:     string | null
}

export interface CallHistoryPage {
  content:       CallHistoryResponse[]
  totalElements: number
  totalPages:    number
  page:          number
  size:          number
}
