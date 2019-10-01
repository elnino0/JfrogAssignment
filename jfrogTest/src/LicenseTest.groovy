import com.sun.xml.internal.ws.api.ha.StickyFeature
import groovy.json.*

import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*
import org.junit.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class LicenseTest {
    def baseUrl = 'http://10.100.102.6:8081/artifactory'
    def user = 'admin'
    def password = 'password'
    def apikey
    def userPasswordAuth = user + ':' + password
    def dockerApiUrl = "http://10.100.102.6:2375"
    def imageSource ="docker.bintray.io/jfrog/artifactory-pro"
    def tag = "latest"

    def imageid
    def  containerId

    @BeforeClass
    def setUp() {
        apikey = getApiKey()
        def License = new File(System.getProperty("user.dir") + '\\resources\\LicenseTwo.txt').getText("UTF-8")
        sendLicenseRequest(License,200,userPasswordAuth)
        imageid = pullDockerImage(imageSource, tag)
        def jsonResponse = createContainer(imageid,8081,"HTTP")
        def slurper = new JsonSlurper().parseText(jsonResponse)
        containerId = slurper.Id
        ActionDockerContainer(containerId,"start")
    }

    @AfterClass
    def afterClass(){
        ActionDockerContainer(containerId,"stop")
    }

    def pullDockerImage(String imageSource, String tag){
        def url = dockerApiUrl + '/v1.24/images/create?fromSrc='+imageSource+'&tag='+tag
        def post = new HttpPost(url)
        def client = HttpClientBuilder.create().build()
        def response = client.execute(post)
        assert response.getStatusLine().getStatusCode() == 204
        return imageId
    }

    def createContainer(String image, int port, String protocol){
        def  map = [:]
        def  portsMap = [:]
        portsMap[port + '/' + protocol] = [:]
        map["image"] = image
        map["ExposedPorts"] = portsMap
        def jsonBody = new JsonBuilder(map).toString()
        def url = dockerApiUrl +   '/containers/create'
        def post = new HttpPost(url)
        post.addHeader("content-type", "application/json")
        post.setEntity(new StringEntity(jsonBody))
        def client = HttpClientBuilder.create().build()
        def response = client.execute(post)
        def bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
        def jsonResponse = bufferedReader.getText()
        println "response: \n" + jsonResponse
        return jsonResponse
    }

    def ActionDockerContainer(String nameOrId,String action){
       // POST /v1.24/containers/e90e34656806/start HTTP/1.1
        def url = baseUrl + '/v1.24/containers/' + nameOrId +'/'+action
        def post = new HttpPost(url)
        def client = HttpClientBuilder.create().build()
        def response = client.execute(post)
        assert response.getStatusLine().getStatusCode() == 200
    }

    class  LicenseInfo{

        LicenseInfo(type, validThrough, licensedTo) {
            this.type = type
            this.validThrough = validThrough
            this.licensedTo = licensedTo
        }
        def type
        def validThrough
        def  licensedTo
    }

    void assertLicenseResponse(String json, String license, int status, String message){
        def slurper = new JsonSlurper().parseText(json)
        assert slurper.messages[license] == message
        assert slurper.status == status
    }

    void assertLicenseInfo(LicenseInfo info){

        def url = baseUrl + '/api/system/licenses'
        def get = new HttpGet(url)
        get.addHeader('Authorization','Basic ' + userPasswordAuth.bytes.encodeBase64())
        def client = HttpClientBuilder.create().build()
        def response = client.execute(get)
        def bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
        def jsonResponse = bufferedReader.getText()
        println "response: \n" + jsonResponse
        def slurper = new JsonSlurper().parseText(jsonResponse)

        assert  slurper.type == info.type
        assert  slurper.validThrough == info.validThrough
        assert  slurper.licensedTo == info.licensedTo


    }

    String getApiKey(){
        def url = baseUrl + '/api/security/apiKey'
        def get = new HttpGet(url)
        get.addHeader('Authorization','Basic ' + userPasswordAuth.bytes.encodeBase64())
        def client = HttpClientBuilder.create().build()
        def response = client.execute(get)
        def bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
        def jsonResponse = bufferedReader.getText()
        println "response api key: \n" + jsonResponse
        def slurper = new JsonSlurper().parseText(jsonResponse)

        if (slurper.apiKey == null){
            return  createApiKey()
        }else {
            return slurper.apiKey
        }
    }

    String createApiKey(){
        def url = baseUrl + '/api/security/apiKey'
        def post = new HttpPost(url)
        post.addHeader('Authorization','Basic ' + userPasswordAuth.bytes.encodeBase64())
        def client = HttpClientBuilder.create().build()
        def response = client.execute(post)
        def bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
        def jsonResponse = bufferedReader.getText()
        println "response: \n" + jsonResponse
        def slurper = new JsonSlurper().parseText(jsonResponse)

        return  slurper.apiKey
    }

    String sendLicenseRequest(String licenseKey, int expectedStatus, String  authorizationValue){
        def map = [:]
        map["licenseKey"] = licenseKey
        def jsonBody = new JsonBuilder(map).toString()
        def url = baseUrl + '/api/system/licenses'
        def post = new HttpPost(url)

        post.addHeader("content-type", "application/json")
        post.addHeader('Authorization','Basic ' + authorizationValue.bytes.encodeBase64())
        post.setEntity(new StringEntity(jsonBody))

        def client = HttpClientBuilder.create().build()
        def response = client.execute(post)

        def bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
        def jsonResponse = bufferedReader.getText()
        println "response: \n" + jsonResponse
        assert response.getStatusLine().getStatusCode() == expectedStatus
        return jsonResponse
    }

    @Test
    void newLicenseKeyTest() {

        def License = new File(System.getProperty("user.dir") + '\\resources\\LicenseTwo.txt').getText("UTF-8")
        println License
        assertLicenseResponse(sendLicenseRequest(License,200,userPasswordAuth),License+"\r\n",200,"Valid.")
        def licenseInfo = new LicenseInfo("Trial","Oct 28, 2019","none")
        assertLicenseInfo(licenseInfo)
    }

    @Test
    void replaceLicenseKeyTest() {

        def License = new File(System.getProperty("user.dir") + '\\resources\\LicenseOne.txt').getText("UTF-8")
        println License
        assertLicenseResponse(sendLicenseRequest(License,200,userPasswordAuth),License+"\r\n",200,"Valid.")
        def licenseInfo = new LicenseInfo("Trial","Oct 27, 2019","none")
        assertLicenseInfo(licenseInfo)
    }

    @Test
    void BadLicenseKeyTest() {

        def License = new File(System.getProperty("user.dir") + '\\resources\\BadLicense.txt').getText("UTF-8")
        println License
        assertLicenseResponse(sendLicenseRequest(License,400, userPasswordAuth),License,400,"Error occurred during license verification.")
    }

    @Test
    void newLicenseKeyTestApi() {
        def License = new File(System.getProperty("user.dir") + '\\resources\\LicenseTwo.txt').getText("UTF-8")
        println License
        assertLicenseResponse(sendLicenseRequest(License,200, user + ':'+ apikey), License+"\r\n",200,"Valid.")
        def licenseInfo = new LicenseInfo("Trial","Oct 28, 2019","none")
        assertLicenseInfo(licenseInfo)
    }



}
