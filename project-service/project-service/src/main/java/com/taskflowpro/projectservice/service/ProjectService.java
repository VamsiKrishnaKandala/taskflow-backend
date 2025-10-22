package com.taskflowpro.projectservice.service;

import com.taskflowpro.projectservice.exception.InvalidProjectDataException;
import com.taskflowpro.projectservice.exception.ProjectNotFoundException;
import com.taskflowpro.projectservice.model.Project;
import com.taskflowpro.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service layer for handling Project business logic with validations and logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {
	
	private final ProjectRepository projectRepository;
	
	/**
     * Creates a new Project with PF-XXX custom ID.
     */
	
	public Mono<Project> createProject(Project project){
		log.info("Creating new project: {}", project.getName());
		
		if (project.getName() == null || project.getName().isBlank()) {
			log.warn("Project name is invalid or missing");
			return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
		}
		
		return projectRepository.findAll()
				.collectList()
				.flatMap(list -> {
					//Generate next PF-XXX ID
					int max = list.stream()
							.map(Project::getId)
							.filter(id -> id != null && id.startsWith("PF-"))
							.map(id -> id.substring(3))
							.mapToInt(Integer::parseInt)
							.max()
							.orElse(0);
					
					String nextId = String.format("PF-%03d", max + 1);
					project.setId(nextId);
					log.info("Assigned ID {} to project {}", nextId, project.getName());
					
					return projectRepository.save(project)
							.doOnSuccess(saved -> log.info("Project created successfully with ID: {}", saved.getId()))
							.doOnError(error -> log.error("Error creating project: {}", error.getMessage()));
				});
		
	}
	
	/**
     * Fetches a single project by ID, throws custom exception if not found.
     */
	
	public Mono<Project> getProjectById(String id){
		log.info("Fetching project with ID: {}", id);
		return projectRepository.findById(id)
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Project not found with ID: {}", id);
					return Mono.error(new ProjectNotFoundException(id));	
				}))
				.doOnNext(p -> log.info("Project retrieved successfully: {}", p.getName()))
				.doOnError(e -> log.error("Error fetching project: {}", e.getMessage()));
	}
	
	/**
    * Fetches all projects.
    */
	public Flux<Project> getAllProjects(){
		log.info("Fetching all projects....");
		return projectRepository.findAll()
				.doOnComplete(() -> log.info("Fetched all projects successfully"))
				.doOnError(e -> log.error("Error fetching projects: {}", e.getMessage()));
		
	}
	
	/**
     * Updates an existing project by ID, or throws if not found.
     */
	public Mono<Project> updateProject(String id, Project updatedProject){
		log.info("Updating project with ID: {}", id);
		
		if(updatedProject.getName() == null || updatedProject.getName().isBlank()) {
			return Mono.error(new InvalidProjectDataException("Project name cannot be empty"));
		}
		
		return projectRepository.findById(id)
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Cannot update non-existent project: {}", id);
					return Mono.error(new ProjectNotFoundException(id));
				}))
				.flatMap(existing -> {
					existing.setName(updatedProject.getName());
					existing.setDescription(updatedProject.getDescription());
					existing.setDeadline(updatedProject.getDeadline());
					existing.setMemberIds(updatedProject.getMemberIds());
                    existing.setTags(updatedProject.getTags());
                    return projectRepository.save(existing);
				})
				.doOnSuccess(p -> log.info("Project updated successfully: {}", p.getId()))
                .doOnError(e -> log.error("Error updating project: {}", e.getMessage()));

	}
	
    

}
