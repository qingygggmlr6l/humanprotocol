/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package io.github.reckart.inception.humanprotocol.adapter;

import static io.github.reckart.inception.humanprotocol.HumanManifestUtils.loadManifest;
import static io.github.reckart.inception.humanprotocol.HumanProtocolController.API_BASE;
import static io.github.reckart.inception.humanprotocol.HumanProtocolController.SUBMIT_JOB;
import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.context.WebApplicationContext;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasStorageService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryProperties;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistryImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.ChainLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistryImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.RelationLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.SpanLayerSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.AnnotationSchemaServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.BackupProperties;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.CasStorageServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.DocumentServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.ImportExportServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.casstorage.OpenCasStorageSessionForRequestFilter;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.export.ProjectExportServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.project.ProjectInitializer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.project.ProjectServiceImpl;
import de.tudarmstadt.ukp.clarin.webanno.project.initializers.TokenLayerInitializer;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDaoImpl;
import de.tudarmstadt.ukp.clarin.webanno.security.model.Role;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.ApplicationContextProvider;
import de.tudarmstadt.ukp.clarin.webanno.text.TextFormatSupport;
import io.github.reckart.inception.humanprotocol.HumanProtocolControllerImpl;
import io.github.reckart.inception.humanprotocol.model.HumanManifest;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "repository.path=target/HumanProtocolControllerImplTest/repository" })
@EnableWebSecurity
@EntityScan({ "de.tudarmstadt.ukp.clarin.webanno.model",
        "de.tudarmstadt.ukp.clarin.webanno.security.model" })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HumanProtocolControllerImplTest
{
    private @Autowired WebApplicationContext context;
    private @Autowired UserDao userRepository;
    private @Autowired ProjectService projectService;

    private MockMvc mvc;

    // If this is not static, for some reason the value is re-set to false before a
    // test method is invoked. However, the DB is not reset - and it should not be.
    // So we need to make this static to ensure that we really only create the user
    // in the DB and clean the test repository once!
    private static boolean initialized = false;

    @Before
    public void setup()
    {
        // @formatter:off
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .alwaysDo(print())
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .addFilters(new OpenCasStorageSessionForRequestFilter())
                .build();
        // @formatter:on

        if (!initialized) {
            userRepository.create(new User("admin", Role.ROLE_ADMIN));
            initialized = true;

            FileSystemUtils.deleteRecursively(new File("target/HumanProtocolControllerImplTest"));
        }
    }

    @Test
    public void t001_thatManifestCanCreateProject() throws Exception
    {
        File manifestFile = new File("src/test/resources/manifest/example.json");

        HumanManifest manifest = loadManifest(manifestFile);

        assertThat(projectService.listProjects()).hasSize(0);

        // @formatter:off
        mvc.perform(post(API_BASE + SUBMIT_JOB)
                .with(csrf().asHeader())
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(readFileToByteArray(manifestFile)))
            .andExpect(status().isCreated());
        // @formatter:on

        assertThat(projectService.existsProject(manifest.getJobId()))
                .as("Project has been created from the job manifest using the job-ID as name")
                .isTrue();

        assertThat(projectService.getProject(manifest.getJobId()))
                .as("Project description has been set from manifest")
                .extracting(Project::getDescription).isEqualTo(manifest.getRequesterDescription());
    }

    @Configuration
    public static class TestContext
    {
        private @Autowired ApplicationEventPublisher applicationEventPublisher;
        private @Autowired EntityManager entityManager;

        @Bean
        public HumanProtocolControllerImpl humanProtocolController(ProjectService aProjectService,
                AnnotationSchemaService aSchemaService)
        {
            return new HumanProtocolControllerImpl(aProjectService, aSchemaService);
        }

        @Bean
        public ProjectService projectService(UserDao aUserRepository,
                ApplicationEventPublisher aApplicationEventPublisher,
                RepositoryProperties aRepositoryProperties,
                @Lazy @Autowired(required = false) List<ProjectInitializer> aInitializerProxy)
        {
            return new ProjectServiceImpl(aUserRepository, applicationEventPublisher,
                    aRepositoryProperties, aInitializerProxy);
        }

        @Bean
        public UserDao userRepository()
        {
            return new UserDaoImpl();
        }

        @Bean
        public TokenLayerInitializer tokenLayerInitializer(
                AnnotationSchemaService aAnnotationSchemaService)
        {
            return new TokenLayerInitializer(aAnnotationSchemaService);
        }

        @Bean
        public DocumentService documentService(RepositoryProperties aRepositoryProperties,
                CasStorageService aCasStorageService, ImportExportService aImportExportService,
                ProjectService aProjectService)
        {
            return new DocumentServiceImpl(aRepositoryProperties, aCasStorageService,
                    aImportExportService, aProjectService, applicationEventPublisher,
                    entityManager);
        }

        @Bean
        public AnnotationSchemaService annotationService()
        {
            return new AnnotationSchemaServiceImpl(layerSupportRegistry(), featureSupportRegistry(),
                    entityManager);
        }

        @Bean
        public FeatureSupportRegistry featureSupportRegistry()
        {
            return new FeatureSupportRegistryImpl(Collections.emptyList());
        }

        @Bean
        public CasStorageService casStorageService()
        {
            return new CasStorageServiceImpl(null, null, repositoryProperties(),
                    backupProperties());
        }

        @Bean
        public ImportExportService importExportService()
        {
            return new ImportExportServiceImpl(repositoryProperties(),
                    asList(new TextFormatSupport()), casStorageService(), annotationService());
        }

        @Bean
        public ProjectExportService exportService(ProjectService aProjectService)
        {
            return new ProjectExportServiceImpl(null, null, aProjectService);
        }

        @Bean
        public RepositoryProperties repositoryProperties()
        {
            return new RepositoryProperties();
        }

        @Bean
        public BackupProperties backupProperties()
        {
            return new BackupProperties();
        }

        @Bean
        public ApplicationContextProvider contextProvider()
        {
            return new ApplicationContextProvider();
        }

        @Bean
        public LayerSupportRegistry layerSupportRegistry()
        {
            return new LayerSupportRegistryImpl(
                    asList(new SpanLayerSupport(featureSupportRegistry(), null, null),
                            new RelationLayerSupport(featureSupportRegistry(), null, null),
                            new ChainLayerSupport(featureSupportRegistry(), null, null)));
        }
    }
}
