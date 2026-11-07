import http from 'k6/http';
import {check,sleep} from 'k6';
export const options={vus:5,duration:'20s',thresholds:{http_req_failed:['rate<0.01']}};
export default function(){const response=http.get(`${__ENV.BASE_URL||'http://host.docker.internal:8088'}/api/session/csrf`);check(response,{'csrf available':r=>r.status===200});sleep(0.2);}
