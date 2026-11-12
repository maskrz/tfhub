package pl.tfhub.api.trainingimport.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportIdException;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotFoundException;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingImportAccessService {

	private final TrainingImportRepository importRepository;
	private final AccessScopeService accessScopeService;

	public TrainingImportAccessService(
			TrainingImportRepository importRepository,
			AccessScopeService accessScopeService) {
		this.importRepository = importRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public TrainingImport getAccessibleImport(long importId) {
		return findAccessibleImport(importId, false);
	}

	@Transactional
	public TrainingImport getAccessibleImportForUpdate(long importId) {
		return findAccessibleImport(importId, true);
	}

	private TrainingImport findAccessibleImport(long importId, boolean lock) {
		if (importId <= 0) {
			throw new InvalidTrainingImportIdException();
		}
		accessScopeService.requireCoachOrAdmin();
		TrainingImport trainingImport = (lock
				? importRepository.findByIdForUpdate(importId)
				: importRepository.findById(importId))
				.orElseThrow(() -> new TrainingImportNotFoundException(importId));
		if (accessScopeService.currentUser().role() == UserRole.COACH
				&& !accessScopeService.assignedGroupIds().contains(trainingImport.getTrainingGroup().getId())) {
			throw new TrainingImportNotFoundException(importId);
		}
		return trainingImport;
	}
}
